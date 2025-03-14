package scala.lms
package epfl
package test3

import test1._
import test2._

trait MatchProg { this: Matching with Extractors =>

  case class Success(x: Int)

  implicit def successTyp: Typ[Success]

  implicit def intTyp: Typ[Int]
  implicit def stringTyp: Typ[String]
  implicit def listTyp[T: Typ]: Typ[List[T]]
  implicit def consTyp[T: Typ]: Typ[::[T]]

  object SuccessR {
    def apply(x: Rep[Int]): Rep[Success] =
      construct(classOf[Success], Success.apply, x)
    def unapply(x: Rep[Success]): Option[Rep[Int]] =
      deconstruct(classOf[Success], Success.unapply, x)
  }

  object :!: {
    def apply[A: Typ](x: Rep[A], xs: Rep[List[A]]) =
      construct(classOf[::[A]], (::.apply[A] _).tupled, tuple(x, xs))
//    def unapply[A](x: Rep[::[A]]) = deconstruct2(classOf[::[A]], ::.unapply[A], x) // doesn't work: hd is private in :: !
    def unapply[A: Typ](x: Rep[List[A]]): Option[(Rep[A], Rep[List[A]])] =
      deconstruct2(
        classOf[::[A]].asInstanceOf[Class[List[A]]],
        (x: List[A]) => Some(x.head, x.tail),
        x
      )
  }

  def infix_unapply(o: SuccessR.type, x: Rep[Success]): Option[Rep[Int]] =
    deconstruct(classOf[Success], Success.unapply, x)
  // doesn't work...

  def test(x: Rep[Success]): Rep[String] = x switch {
    case SuccessR(x) if x guard 7 => unit("yes")
  } orElse { case SuccessR(x) =>
    unit("maybe")
  } orElse { case _ =>
    unit("no")
  }

  def testXX(x: Rep[Success]): Rep[String] = _match(x)(
    {
      case SuccessR(x) if x guard 7 => unit("yes")
    },
    { case SuccessR(x) =>
      unit("maybe")
    },
    { case _ =>
      unit("no")
    }
  )
}

trait MatchProgExp0 extends common.BaseExp with MatchProg {
  this: Matching with Extractors =>
  implicit def successTyp: Typ[Success] = manifestTyp

  implicit def intTyp: Typ[Int] = manifestTyp
  implicit def stringTyp: Typ[String] = manifestTyp
  implicit def listTyp[T: Typ]: Typ[List[T]] = {
    implicit val ManifestTyp(m) = typ[T]
    manifestTyp
  }
  implicit def consTyp[T: Typ]: Typ[::[T]] = {
    implicit val ManifestTyp(m) = typ[T]
    manifestTyp
  }

}

class TestMatch extends FileDiffSuite {

  val prefix = home + "test-out/epfl/test3-"

  /*
      println {
        object TestMatchString extends TestMatch with Matching with Extractors with MatchingExtractorsRepString
        import TestMatchString._
        test(SuccessR("7"))
      }
   */

  def testMatch1 = {
    withOutFile(prefix + "match1") {
      object MatchProgExp
          extends MatchProgExp0
          with Matching
          with Extractors
          with MatchingExtractorsExp
          with FunctionsExpUnfoldAll
          with Control
          with FlatResult
          with DisableCSE
      import MatchProgExp._

      val r = reifyEffects(test(fresh[Success]))
      println(globalDefs.mkString("\n"))
      println(r)
      val p = new ExtractorsGraphViz {
        val IR: MatchProgExp.type = MatchProgExp
      }
      p.emitDepGraph(result[Unit](r), prefix + "match1-dot")
    }
    assertFileEqualsCheck(prefix + "match1")
    assertFileEqualsCheck(prefix + "match1-dot")
  }

  def testMatch2 = {
    withOutFile(prefix + "match2") {
      object MatchProgExp
          extends MatchProgExp0
          with Matching
          with Extractors
          with MatchingExtractorsExpOpt
          with FunctionsExpUnfoldAll
          with Control
          with FlatResult
      import MatchProgExp._

      val r = reifyEffects(test(fresh[Success]))
      println(globalDefs.mkString("\n"))
      println(r)
      val p = new ExtractorsGraphViz {
        val IR: MatchProgExp.type = MatchProgExp
      }
      p.emitDepGraph(result[Unit](r), prefix + "match2-dot")
    }
    assertFileEqualsCheck(prefix + "match2")
    assertFileEqualsCheck(prefix + "match2-dot")
  }

}
