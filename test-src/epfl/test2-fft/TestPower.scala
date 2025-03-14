package scala.lms
package epfl
package test2

import common._
import test1._
import reflect.SourceContext

import java.io.PrintWriter

trait Power1 { this: Arith =>
  def power(b: Rep[Double], x: Int): Rep[Double] =
    if (x == 0) 1.0 else b * power(b, x - 1)
}

trait Power2 { this: Arith =>
  def power(b: Rep[Double], x: Int)(implicit
      pos: SourceContext
  ): Rep[Double] = {
    if (x == 0) 1.0
    else if ((x & 1) == 0) { val y = power(b, x / 2); y * y }
    else b * power(b, x - 1)
  }
}

trait BaseStr extends Base {
  type Rep[+T] = String
  // todo added this to provide required unit implicit conversion
  implicit def unit[T: Typ](x: T): Rep[T] = x.toString

  case class Typ[T](m: Manifest[T])

  def typ[T: Typ]: Typ[T] = implicitly[Typ[T]]

  implicit def unitTyp: Typ[Unit] = Typ(implicitly)
  implicit def nullTyp: Typ[Null] = Typ(implicitly)
}

trait ArithStr extends Arith with BaseStr {
  // todo removed below
  // implicit def unit(x: Double) = x.toString

  implicit def intTyp: Typ[Int] = Typ(implicitly)
  implicit def doubleTyp: Typ[Double] = Typ(implicitly)

  def infix_+(x: Rep[Double], y: Rep[Double])(implicit pos: SourceContext) =
    "(%s+%s)".format(x, y)
  def infix_-(x: Rep[Double], y: Rep[Double])(implicit pos: SourceContext) =
    "(%s-%s)".format(x, y)
  def infix_*(x: Rep[Double], y: Rep[Double])(implicit pos: SourceContext) =
    "(%s*%s)".format(x, y)
  def infix_/(x: Rep[Double], y: Rep[Double])(implicit pos: SourceContext) =
    "(%s/%s)".format(x, y)
}

class TestPower extends FileDiffSuite {

  val prefix = home + "test-out/epfl/test2-"

  def testPower = {
    withOutFile(prefix + "power") {
      /*
    println {
      val o = new TestPower with ArithRepDirect
      import o._
      power(2,4)
    }

    println {
      val o = new TestPower with ArithRepString
      import o._
      power(2,4)
    }

    println {
      val o = new TestPower with ArithRepString
      import o._
      power("x",4)
    }

    println {
      val o = new TestPower with ArithRepString
      import o._
      power("(x + y)",4)
    }
       */
      {
        val o = new Power1 with ArithStr
        import o._

        val r = power(infix_+("x0", "x1"), 4)
        println(r)
      }
      {
        val o = new Power2 with ArithStr
        import o._

        val r = power(infix_+("x0", "x1"), 4)
        println(r)
      }
      {
        val o = new Power1 with ArithExp
        import o._

        val r = power(fresh[Double] + fresh[Double], 4)
        println(globalDefs.mkString("\n"))
        println(r)
        val p = new ExportGraph { val IR: o.type = o }
        p.emitDepGraph(r, prefix + "power1-dot")
      }

      {
        val o = new Power1 with ArithExpOpt
        import o._

        val r = power(fresh[Double] + fresh[Double], 4)
        println(globalDefs.mkString("\n"))
        println(r)
        val p = new ExportGraph { val IR: o.type = o }
        p.emitDepGraph(r, prefix + "power2-dot")
      }
      {
        val o = new Power1 with ArithExpOpt
        import o._
        val f = (x: Rep[Double]) => power(x + x, 4)
        val p = new ScalaGenFlat with ScalaGenArith { val IR: o.type = o }
        p.emitSource(f, "Power2", new PrintWriter(System.out))
      }

      {
        val o = new Power2 with ArithExpOpt
        import o._

        val r = power(fresh[Double] + fresh[Double], 4)
        println(globalDefs.mkString("\n"))
        println(r)
        val p = new ExportGraph { val IR: o.type = o }
        p.emitDepGraph(r, prefix + "power3-dot")
      }
      {
        val o = new Power2 with ArithExpOpt
        import o._
        val f = (x: Rep[Double]) => power(x + x, 4)
        val p = new ScalaGenFlat with ScalaGenArith { val IR: o.type = o }
        p.emitSource(f, "Power3", new PrintWriter(System.out))
      }

      {
        val o = new Power1 with ArithExpOpt with CompileScala { self =>
          val codegen = new ScalaGenFlat with ScalaGenArith {
            val IR: self.type = self
          }
        }
        import o._

        val power4 = (x: Rep[Double]) => power(x, 4)
        codegen.emitSource(power4, "Power4", new PrintWriter(System.out))
        val power4c = compile(power4)
        println(power4c(2))
      }
    }
    assertFileEqualsCheck(prefix + "power")
    assertFileEqualsCheck(prefix + "power1-dot")
    assertFileEqualsCheck(prefix + "power2-dot")
    assertFileEqualsCheck(prefix + "power3-dot")
  }
}
