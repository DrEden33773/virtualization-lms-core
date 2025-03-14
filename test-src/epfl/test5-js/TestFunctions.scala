package scala.lms
package epfl
package test5

import common._
import test1._

import java.io.PrintWriter
import java.io.FileOutputStream

trait JSGenFunctions extends JSGenEffect with BaseGenFunctions {
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = rhs match {
    case Lambda(fun, x, y) =>
      stream.println("var " + quote(sym) + " = function(" + quote(x) + ") {")
      emitBlock(y)
      stream.println("return " + quote(getBlockResult(y)))
      stream.println("}")

    case Apply(fun, arg) =>
      emitValDef(sym, quote(fun) + "(" + quote(arg) + ")")

    case _ => super.emitNode(sym, rhs)
  }
}

trait JSGenTupledFunctions extends JSGenFunctions {
  val IR: TupledFunctionsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = rhs match {
    case Lambda(fun, UnboxedTuple(xs), y) =>
      stream.println(
        "var " + quote(sym) + " = function" + xs
          .map(quote)
          .mkString("(", ",", ")") + " {"
      )
      emitBlock(y)
      stream.println("return " + quote(getBlockResult(y)))
      stream.println("}")

    case Apply(fun, UnboxedTuple(args)) =>
      emitValDef(sym, quote(fun) + args.map(quote).mkString("(", ",", ")"))

    case _ => super.emitNode(sym, rhs)
  }

  override def quote(x: Exp[Any]): String = x match {
    case UnboxedTuple(t) =>
      t.zipWithIndex
        .map({ case (el, i) => "_" + (i + 1) + ":" + quote(el) })
        .mkString("{", ",", "}")
    case _ => super.quote(x)
  }
}

trait JSGenStruct extends JSGenBase {
  val IR: StructExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = rhs match {
    case Struct(tag, elems) =>
      registerStruct(structName(sym.tp), elems)
      emitValDef(
        sym,
        "{__structName:" + structName(sym.tp) + "," + elems
          .map(e => e._1 + ":" + quote(e._2))
          .mkString(",") + "}"
      )
//      printlog("WARNING: emitting " + structName(sym.tp) + " struct " + quote(sym))
    case FieldApply(struct, index) =>
      emitValDef(sym, quote(struct) + "." + index)
//      printlog("WARNING: emitting field access: " + quote(struct) + "." + index)
    case FieldUpdate(struct, index, rhs) =>
      emitValDef(sym, quote(struct) + "." + index + " = " + quote(rhs))
//      printlog("WARNING: emitting field update: " + quote(struct) + "." + index)
    case _ => super.emitNode(sym, rhs)
  }
}

trait JSGenTupleOps extends JSGenBase with JSGenStruct {
  val IR: TupleOpsExp
  import IR._

  /* FIXME: now using structs to implement tuples
  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = rhs match {
    case ETuple2(a,b)  =>
      emitValDef(sym, "{_1:"+ quote(a) + ",_2:" + quote(b) + "}")
    case Tuple2Access1(t) => emitValDef(sym, quote(t) + "._1")
    case Tuple2Access2(t) => emitValDef(sym, quote(t) + "._2")

    case _ => super.emitNode(sym, rhs)
  }
   */
}

trait FunctionsProg { this: Print with Functions with IfThenElse with Equal =>
  def test(x: Rep[Any]): Rep[Any] = {
    val f = fun { x: Rep[Any] =>
      print("foo")
      x
    }
    f(f(x))
  }

  def test2(x: Rep[Double]): Rep[Double => Double] =
    fun { (y: Rep[Double]) => if (y == x) unit(2.0) else y }
}

trait FunctionsRecursiveProg extends LiftAll {
  this: PrimitiveOps with Print with Functions =>
  def test(x: Rep[Any]): Rep[Any] = {
    val f = fun { x: Rep[Any] =>
      print("foo")
      x
    }
    lazy val g: Rep[Any => Any] = fun { x =>
      print("bar")
      g(x)
    }
    val h = fun { x: Rep[Any] =>
      print("baz")
      1
    }
    h(g(f(f(x))))
  }
}

trait TwoArgsFunProg {
  this: LiftPrimitives with PrimitiveOps with TupledFunctions =>
  def test(x: Rep[Double]): Rep[(Double, Double)] = {
    val f = fun { (a: Rep[Double], b: Rep[Double]) => (b, a) }
    f(f(x, x))
  }
}

trait TupleFunProg {
  this: LiftPrimitives with PrimitiveOps with TupledFunctions =>
  def test(x: Rep[Double]): Rep[(Double, Double)] = {
    val f = fun { t: Rep[(Double, Double)] => t }
    f(1.0, x)
  }
}

trait NoArgFunProg { this: Print with TupledFunctions =>
  def test(x: Rep[Any]): Rep[Any] = {
    val f = fun { () => x }
    f()
  }
}

trait TwoArgsRecursiveFunProg {
  this: TupledFunctions with PrimitiveOps with Equal with IfThenElse =>
  def test(x: Rep[Double]): Rep[Double] = {
    lazy val iter: Rep[((Double, Double)) => Double] = fun { (n, acc) =>
      if (n == 0) acc else iter(n - 1, n * acc)
    }
    iter(x, unit(1.0))
  }
}

trait SchedFunProg {
  this: Functions with PrimitiveOps with Equal with IfThenElse =>
  def foo: Rep[Double => Double] = fun { a =>
    def iter: Rep[Double => Double] = fun { b =>
      if (b == 0) a
      else iter(b - 1)
    }
    iter(a)
  }

  def test(x: Rep[Double]): Rep[Double] = {
    foo(x)
  }
}

class TestFunctions extends FileDiffSuite {

  val prefix = home + "test-out/epfl/test5-"

  def testFunctions = {
    withOutFile(prefix + "functions") {

      println("-- begin")

      new FunctionsProg
        with PrintExp
        with PrimitiveOpsExp
        with StringOpsExp
        with ArrayOpsExp
        with SeqOpsExp
        with FunctionsExp
        with IfThenElseExp
        with EqualExp { self =>
        val codegen = new ScalaGenPrint
          with ScalaGenFunctions
          with ScalaGenIfThenElse
          with ScalaGenEqual { val IR: self.type = self }

        val f = (x: Rep[Double]) => test(x)
        codegen.emitSource(f, "Test", new PrintWriter(System.out))

        val g = (x: Rep[Double]) => test2(x)
        codegen.emitSource(g, "Test2", new PrintWriter(System.out))
      }

      new FunctionsProg
        with PrintExp
        with PrimitiveOpsExp
        with StringOpsExp
        with ArrayOpsExp
        with SeqOpsExp
        with FunctionsExp
        with IfThenElseExp
        with EqualExp { self =>
        val codegen = new JSGenPrint
          with JSGenFunctions
          with JSGenIfThenElse
          with JSGenEqual { val IR: self.type = self }

        val f = (x: Rep[Double]) => test(x)
        codegen.emitSource(f, "main", new PrintWriter(System.out))

        val g = (x: Rep[Double]) => test2(x)
        codegen.emitSource(g, "main2", new PrintWriter(System.out))
      }

      println("-- end")
    }
    assertFileEqualsCheck(prefix + "functions")
  }

  def testFunRetFun = {
    withOutFile(prefix + "funretfun") {

      println("-- begin")

      new FunctionsProg
        with PrintExp
        with PrimitiveOpsExp
        with StringOpsExp
        with ArrayOpsExp
        with SeqOpsExp
        with FunctionsExp
        with IfThenElseExp
        with EqualExp { self =>
        val codegen = new ScalaGenPrint
          with ScalaGenFunctions
          with ScalaGenIfThenElse
          with ScalaGenEqual {
          val IR: self.type = self
        }

        val f = (x: Rep[Double]) => doLambda { (y: Rep[Int]) => test(x) }
        codegen.emitSource(f, "Test", new PrintWriter(System.out))
      }

      new FunctionsProg
        with PrintExp
        with PrimitiveOpsExp
        with StringOpsExp
        with ArrayOpsExp
        with SeqOpsExp
        with FunctionsExp
        with IfThenElseExp
        with EqualExp { self =>
        val codegen = new JSGenPrint
          with JSGenFunctions
          with JSGenIfThenElse
          with JSGenEqual { val IR: self.type = self }

        val f = (x: Rep[Double]) => doLambda { (y: Rep[Int]) => test(x) }
        codegen.emitSource(f, "main", new PrintWriter(System.out))
      }

      println("-- end")
    }
    assertFileEqualsCheck(prefix + "funretfun")
  }

  def testFunctionsRecursive = {
    withOutFile(prefix + "functionsrecursive") {

      println("-- begin")

      new FunctionsRecursiveProg
        with FunctionsRecursiveExp
        with PrintExp
        with PrimitiveOpsExp
        with StringOpsExp
        with ArrayOpsExp
        with SeqOpsExp
        with FunctionsExp
        with IfThenElseExp
        with EqualExp { self =>
        val codegen = new ScalaGenPrimitiveOps
          with ScalaGenPrint
          with ScalaGenFunctions { val IR: self.type = self }

        val f = (x: Rep[Double]) => test(x)
        codegen.emitSource(f, "Test", new PrintWriter(System.out))
      }

      new FunctionsRecursiveProg
        with FunctionsRecursiveExp
        with PrintExp
        with PrimitiveOpsExp
        with StringOpsExp
        with ArrayOpsExp
        with SeqOpsExp
        with FunctionsExp
        with IfThenElseExp
        with EqualExp { self =>
        val codegen = new JSGenPrimitiveOps
          with JSGenPrint
          with JSGenFunctions { val IR: self.type = self }

        val f = (x: Rep[Double]) => test(x)
        codegen.emitSource(f, "main", new PrintWriter(System.out))
      }

      println("-- end")
    }
    assertFileEqualsCheck(prefix + "functionsrecursive")
  }

  def testTwoArgsFun = {
    withOutFile(prefix + "twoargsfun") {
      new TwoArgsFunProg
        with LiftPrimitives
        with TupledFunctionsExp
        with PrintExp
        with PrimitiveOpsExp
        with StringOpsExp
        with ArrayOpsExp
        with SeqOpsExp
        with FunctionsExp
        with IfThenElseExp
        with EqualExp { self =>
        val codegen = new JSGenTupledFunctions
          with JSGenTupleOps
          with GenericGenUnboxedTupleAccess { val IR: self.type = self }
        codegen.emitSource(test _, "main", new PrintWriter(System.out))
      }
    }
    assertFileEqualsCheck(prefix + "twoargsfun")
  }

  def testTupleFun = {
    withOutFile(prefix + "tuplefun") {
      new TupleFunProg
        with LiftPrimitives
        with TupledFunctionsExp
        with PrintExp
        with PrimitiveOpsExp
        with StringOpsExp
        with ArrayOpsExp
        with SeqOpsExp
        with FunctionsExp
        with IfThenElseExp
        with EqualExp { self =>
        val codegen = new JSGenTupledFunctions
          with JSGenTupleOps
          with GenericGenUnboxedTupleAccess { val IR: self.type = self }
        codegen.emitSource(test _, "main", new PrintWriter(System.out))
      }
    }
    assertFileEqualsCheck(prefix + "tuplefun")
  }

  def testNoArgFun = {
    withOutFile(prefix + "noargfun") {
      new NoArgFunProg
        with TupledFunctionsRecursiveExp
        with PrintExp
        with PrimitiveOpsExp
        with StringOpsExp
        with ArrayOpsExp
        with SeqOpsExp
        with FunctionsExp
        with IfThenElseExp
        with EqualExp { self =>
        val codegen = new JSGenTupledFunctions
          with JSGenTupleOps
          with GenericGenUnboxedTupleAccess { val IR: self.type = self }
        codegen.emitSource(test _, "main", new PrintWriter(System.out))
      }
    }
    assertFileEqualsCheck(prefix + "noargfun")
  }

  def testTwoArgsRecursiveFun = {
    withOutFile(prefix + "twoargsrecfun") {
      new TwoArgsRecursiveFunProg
        with TupledFunctionsRecursiveExp
        with PrintExp
        with PrimitiveOpsExp
        with StringOpsExp
        with ArrayOpsExp
        with SeqOpsExp
        with FunctionsExp
        with IfThenElseExp
        with EqualExp { self =>
        val codegen = new JSGenTupledFunctions
          with JSGenPrimitiveOps
          with JSGenEqual
          with JSGenIfThenElse
          with JSGenTupleOps
          with GenericGenUnboxedTupleAccess { val IR: self.type = self }
        codegen.emitSource(test _, "main", new PrintWriter(System.out))
      }
    }
    assertFileEqualsCheck(prefix + "twoargsrecfun")
  }

  def testSchedFun = {
    withOutFile(prefix + "schedfun") {
      new SchedFunProg
        with FunctionsRecursiveExp
        with PrintExp
        with PrimitiveOpsExp
        with StringOpsExp
        with ArrayOpsExp
        with SeqOpsExp
        with FunctionsExp
        with IfThenElseExp
        with EqualExp { self =>
        val codegen = new JSGenFunctions
          with JSGenPrimitiveOps
          with JSGenEqual
          with JSGenIfThenElse { val IR: self.type = self }
        val f = (x: Rep[Double]) => test(x)
        codegen.emitSource(f, "Test", new PrintWriter(System.out))
      }
    }
    assertFileEqualsCheck(prefix + "schedfun")
  }

}
