package scala.lms
package epfl
package test8

import common._
import internal.{NestedBlockTraversal}
import test1._
import test7.{Print, PrintExp, ScalaGenPrint}
import test7.{ArrayLoops, ArrayLoopsExp, ScalaGenArrayLoops}

import util.OverloadHack

import java.io.{PrintWriter, StringWriter, FileOutputStream}
import scala.reflect.SourceContext

class TestSpeculative extends FileDiffSuite {

  val prefix = home + "test-out/epfl/test8-"

  trait DSL
      extends ArrayMutation
      with PrimitiveOps
      with LiftPrimitives
      with OrderingOps
      with BooleanOps
      with LiftVariables
      with IfThenElse
      with While
      with RangeOps
      with Print {
    def zeros(l: Rep[Int]) = array(l) { i => 0 }
    def mzeros(l: Rep[Int]) = zeros(l).mutable
    def infix_toDouble(x: Rep[Int]): Rep[Double] = x.asInstanceOf[Rep[Double]]

    def test(x: Rep[Int]): Rep[Unit]
  }
  trait Impl
      extends DSL
      with ArrayMutationExp
      with PrimitiveOpsExp
      with OrderingOpsExpOpt
      with BooleanOpsExp
      with EqualExpOpt
      with VariablesExpOpt
      with StringOpsExp
      with IfThenElseExpOpt
      with WhileExpOptSpeculative
      with SplitEffectsExpFat
      with RangeOpsExp
      with PrintExp
      with CompileScala { self =>
    override val verbosity = 1
    val codegen = new ScalaGenArrayMutation
      with ScalaGenPrimitiveOps
      with ScalaGenOrderingOps
      with ScalaGenVariables
      with ScalaGenIfThenElseFat
      with ScalaGenWhileOptSpeculative
      with ScalaGenSplitEffects
      with ScalaGenRangeOps
      with ScalaGenPrint /*with LivenessOpt*/ { val IR: self.type = self }
    codegen.emitSource(test, "Test", new PrintWriter(System.out))
    val f = compile(test)
    f(7)
  }

  def testSpeculative1 = {
    withOutFile(prefix + "speculative1") {
      // test simple copy propagation through variable
      trait Prog extends DSL {
        def test(x: Rep[Int]) = {
          var x = 7

          if (x > 3) // should remove conditional
            print(x)
          else
            print("no")
          print(x)
        }
      }
      new Prog with Impl
    }
    assertFileEqualsCheck(prefix + "speculative1")
  }

  def testSpeculative1b = {
    withOutFile(prefix + "speculative1b") {
      // test simple copy propagation through variable
      trait Prog extends DSL {
        def test(x: Rep[Int]) = {
          var x = 7

          if (x > 3) // should remove conditional
            x = 5
          else
            print("no")

          print(x) // should be const 5
        }
      }
      new Prog with Impl
    }
    assertFileEqualsCheck(prefix + "speculative1b")
  }

  def testSpeculative1c = {
    withOutFile(prefix + "speculative1c") {
      // test simple copy propagation through variable
      trait Prog extends DSL {
        def test(y: Rep[Int]) = {
          var x = 7

          if (x > y) // cannot remove conditional
            x = 5
          else
            print("no")

          print(x) // should be var read
        }
      }
      new Prog with Impl
    }
    assertFileEqualsCheck(prefix + "speculative1c")
  }

  def testSpeculative1d = {
    withOutFile(prefix + "speculative1d") {
      // test simple copy propagation through variable
      trait Prog extends DSL {
        def test(y: Rep[Int]) = {
          var x = 7
          var z = 9 // should remove z because it is never read

          if (x > y) { // cannot remove conditional
            x = 5
            z = 12 // assignment should be removed, too
          } else
            print("no")

          print(x) // should be var read
        }
      }
      new Prog with Impl
    }
    assertFileEqualsCheck(prefix + "speculative1d")
  }

  def testSpeculative3 = {
    withOutFile(prefix + "speculative3") {
      // test simple copy propagation through variable
      trait Prog extends DSL {
        def test(x: Rep[Int]) = {
          var x = 7
          var c = 0.0
          while (c < 10.0) {
            print(x) // should be const 7
            print(c)
            var z = 2 // should remove var
            c = c + 1
            print(z) // should be const 2
          }
          print(x) // should be const 7
          print(c)
        }
      }
      new Prog with Impl
    }
    assertFileEqualsCheck(prefix + "speculative3")
  }

  def testSpeculative3b = {
    withOutFile(prefix + "speculative3b") {
      // test simple copy propagation through variable
      trait Prog extends DSL {
        def test(x: Rep[Int]) = {
          var x = 7
          var y = 4.0 // should remove
          var c = 0.0
          while (c < 10.0) {
            print(x) // should be const 7
            print(c)
            var z = 2 // should remove var
            c = c + 1
            print(z) // should be const 2
            y = y + 2 // should remove
          }
          print(x) // should be const 7
          print(c)
        }
      }
      new Prog with Impl
    }
    assertFileEqualsCheck(prefix + "speculative3b")
  }

  def testSpeculative4 = {
    withOutFile(prefix + "speculative4") {
      // test simple copy propagation through variable
      trait Prog extends DSL {
        def test(x: Rep[Int]) = {
          var c = 0.0
          while (c > 10.0) {
            print("booooring!")
          }
          print("done")
        }
      }
      new Prog with Impl
    }
    assertFileEqualsCheck(prefix + "speculative4")
  }

  def testSpeculative5 = {
    withOutFile(prefix + "speculative5") {
      // test simple copy propagation through variable
      trait Prog extends DSL {
        def test(x: Rep[Int]) = {
          var x = 7.0
          var c = 0.0
          while (c < 10.0) {
            if (x < 10.0)
              print("test")
            else
              x = c
            print(x)
            c += 1.0
          }
          print(x)
        }
      }
      new Prog with Impl
    }
    assertFileEqualsCheck(prefix + "speculative5")
  }

  // FIXME: this one breaks. Variable j is lifted to
  // top scope because it is not part of the mayWrite
  // summary of the inner loop.
  def testSpeculative6 = {
    withOutFile(prefix + "speculative6") {
      // test simple copy propagation through variable
      trait Prog extends DSL {
        def test(x: Rep[Int]) = {
          print("FIXME -- WRONG RESULT")
          var i = 0
          while (i < 10) {
            var j = 0
            while (j < 10) {
              print("test")
              print(i)
              print(j)
              j += 1
            }
            i += 1
          }
        }
      }
      new Prog with Impl
    }
    assertFileEqualsCheck(prefix + "speculative6")
  }

}
