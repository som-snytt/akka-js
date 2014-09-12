package akkajs.test

import scala.scalajs.js
import js.annotation.JSExport
import scala.collection.immutable.Seq
import akka.actor.{DynamicAccess, JSDynamicAccess}


@JSExport
class Person(name: String, age: Int) {
  override val toString = s"Person($name,$age)"
}

@JSExport
class EntitySingleton

@JSExport
object PersonSingleton extends EntitySingleton

trait EntitySingleton2

@JSExport
object PersonSingleton2 extends EntitySingleton2


class DynamicAccessTestSuite extends TestSuite with AsyncAssert {

  def numTests: Int = 5

  def testMain(): Unit = {
    val dynAccess: DynamicAccess = new JSDynamicAccess
    val objTry = dynAccess.createInstanceFor[Person](classOf[Person], Seq(classOf[String] -> "Hans", classOf[java.lang.Integer] -> new java.lang.Integer(60)))
    assert(objTry.toString == "Success(Person(Hans,60))", s"something's very wrong: $objTry")

    val objTry2 = dynAccess.createInstanceFor[Person]("Person", Seq(classOf[String] -> "Hans", classOf[java.lang.Integer] -> new java.lang.Integer(60)))
    assert(objTry2.toString == "Success(Person(Hans,60))", s"something's very wrong: $objTry2")

    val objTry3 = dynAccess.getObjectFor[EntitySingleton]("PersonSingleton")
    assert(objTry3.isSuccess, s"!isSuccess: $objTry3")

    val obj3 = objTry3.get
    assert(obj3 == PersonSingleton, s"expected: $PersonSingleton, got: $obj3")

    val objTry4 = dynAccess.getObjectFor[EntitySingleton2]("PersonSingleton2")
    assert(objTry4.isSuccess, s"!isSuccess: $objTry4")
  }

}
