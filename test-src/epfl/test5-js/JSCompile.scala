package scala.lms
package epfl
package test5

import common._
import internal._
import test1._

import java.io.PrintWriter

trait JSCodegen extends GenericCodegen {
  import IR._

  def emitHTMLPage[B](f: () => Exp[B], stream: PrintWriter)(implicit
      mB: Typ[B]
  ): Unit = {
    stream.println(
      "<html><head><title>Scala2JS</title><script type=\"text/JavaScript\">"
    )

    // FIXME move unitTyp from BaseExp to Expressions or change IR required type to BaseExp
    implicit val unitTyp: Typ[Unit] = ManifestTyp(implicitly)

    emitSource((x: Exp[Unit]) => f(), "main", stream)

    stream.println("</script><body onload=\"main(0)\">")
    stream.println("</body></html>")
    stream.flush
  }

  def emitSource[A: Typ](
      args: List[Sym[_]],
      body: Block[A],
      methName: String,
      out: PrintWriter
  ) = {
    withStream(out) {
      stream.println(
        "function " + methName + "(" + args.map(quote).mkString(", ") + ") {"
      )

      emitBlock(body)
      stream.println("return " + quote(getBlockResult(body)))

      stream.println("}")
    }
    Nil
  }
  def emitValDef(sym: Sym[Any], rhs: String): Unit = {
    stream.println("var " + quote(sym) + " = " + rhs)
  }
}

trait JSNestedCodegen extends GenericNestedCodegen with JSCodegen {
  import IR._

}

trait JSGenBase extends JSCodegen {
  val IR: BaseExp
}

trait JSGenEffect extends JSNestedCodegen with JSGenBase {
  val IR: EffectExp
}

trait JSGenIfThenElse extends BaseGenIfThenElse with JSGenEffect { // it's more or less generic...
  val IR: IfThenElseExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = rhs match {
    case IfThenElse(c, a, b) =>
      stream.println("var " + quote(sym))
      stream.println("if (" + quote(c) + ") {")
      emitBlock(a)
      stream.println(quote(sym) + "=" + quote(getBlockResult(a)))
      stream.println("} else {")
      emitBlock(b)
      stream.println(quote(sym) + "=" + quote(getBlockResult(b)))
      stream.println("}")
    case _ => super.emitNode(sym, rhs)
  }
}

trait JSGenPrimitiveOps extends JSGenBase { // TODO: define a generic one
  val IR: PrimitiveOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = rhs match {
    case DoublePlus(a, b)   => emitValDef(sym, "" + quote(a) + "+" + quote(b))
    case DoubleMinus(a, b)  => emitValDef(sym, "" + quote(a) + "-" + quote(b))
    case DoubleTimes(a, b)  => emitValDef(sym, "" + quote(a) + "*" + quote(b))
    case DoubleDivide(a, b) => emitValDef(sym, "" + quote(a) + "/" + quote(b))
    case _                  => super.emitNode(sym, rhs)
  }
}
