package spinal.core

import scala.reflect.runtime.universe._
import scala.collection.mutable.ArrayBuffer

object IsInterface extends SpinalTag {}

/** system verilog interface
  * 
  * ==Example==
  *{{{
  * case class MyIf() extends Interface with IMasterSlave {
  *  val a = Bits(8 bits)
  *  val b = Bool()
  *  val c = SInt(8 bits)
  *
  *  override def asMaster = mst
  *  @modport
  *  def mst = {
  *    out(a, b, c)
  *  }
  *
  *  @modport
  *  def slv = {
  *    in(a, b, c)
  *  }
  *
  *}
  *}}}
  *
  */
class Interface extends Bundle {
  var definitionName: String = this.getClass.getSimpleName
  /** Set the definition name of the component */
  def setDefinitionName(name: String): this.type = {
    definitionName = name
    this
  }
  val genericElements = ArrayBuffer[(String, Any)]()
  def addGeneric(name : String, that : Any): String = {
    that match {
      case bt: BaseType => genericElements += Tuple2(name, bt.addTag(GenericValue(bt.head.source)))
      case vv: VerilogValues => genericElements += Tuple2(name, vv)
      case s: String    => genericElements += Tuple2(name, s)
      case i: Int       => genericElements += Tuple2(name, i)
      case i: BigInt if i <= Integer.MAX_VALUE && i >= Integer.MIN_VALUE => genericElements += Tuple2(name, i.toInt)
      case d: Double        => genericElements += Tuple2(name, d)
      case boolean: Boolean => genericElements += Tuple2(name, boolean)
      case t: TimeNumber    => genericElements += Tuple2(name, t)
    }
    name
  }
  val widthGeneric = scala.collection.mutable.LinkedHashMap[Data, String]()
  def tieGeneric[T <: Data](signal: T, generic: String) = {
    widthGeneric += signal -> generic
  }
  override def valCallbackRec(ref: Any, name: String): Unit = {
    ref match {
      case ref : Data => {
        ref match {
          case ref: BaseType => {
            ref match {
              case _: SpinalEnumCraft[_] => {
                LocatedPendingError(s"sv interface is still under develop. by now SpinalEnum is not allowed")
              }
              case ref => {
                ref.addTag(IsInterface)
              }
            }
          }
          case _ => {
            LocatedPendingError(s"sv interface is still under develop. by now only BaseType is allowed")
          }
        }
      }
      case ref =>
    }
    super.valCallbackRec(ref, name)
  }
  def allModPort: List[String] = {
    this.getClass.getMethods()
      .filter(m => {
        m.getAnnotation(classOf[modport]) != null
      })
      .map(m => m.getName())
      .toList
  }
  def callModPort(s: String): Unit = {
    this.getClass.getMethod(s).invoke(this).asInstanceOf[Unit]
  }
  def checkModport() = {
    allModPort
      .filter(x => {
        val t = this
        val toplevel = globalData.toplevel
        val phase = globalData.phaseContext.topLevel
        globalData.toplevel = null
        globalData.phaseContext.topLevel = null
        val c = new Component {
          val y = t.clone().asInstanceOf[t.type]
          y.callModPort(x)
        }
        globalData.toplevel = toplevel
        globalData.phaseContext.topLevel = phase
        
        c.y.elements.foldLeft(true) {case (dirSame, (name, element)) =>
          val other = this.find(name)
          if (other == null) {
            LocatedPendingError(s"Bundle assignment is not complete. Missing $name")
            false
          } else element match {
            case b: Bundle => b.checkDir(other.asInstanceOf[Bundle]) && dirSame
            case b         => (b.dir == other.dir) && dirSame
          }
        }
      })
  }
}
