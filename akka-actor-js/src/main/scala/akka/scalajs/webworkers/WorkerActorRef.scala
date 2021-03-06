package akka.scalajs.webworkers

import scala.scalajs.js
import org.scalajs.spickling.PicklerRegistry
import org.scalajs.spickling.jsany._

import akka.actor._
import akka.dispatch.sysmsg.SystemMessage

private[akka] class WorkerActorRef(
  system: WebWorkersActorSystem,
  val path: ActorPath) extends InternalActorRef {

  private[this] val pickledPath = PicklerRegistry.pickle(path)

  private[this] lazy val parent =
    if (path.isInstanceOf[RootActorPath]) Nobody
    else new WorkerActorRef(system, path.parent)

  def !(msg: Any)(implicit sender: ActorRef): Unit = {
    system.sendMessageAcrossWorkers(path.address, msg, this, sender)
  }

  // InternalActorRef API

  // TODO
  def start(): Unit = ()
  def resume(causedByFailure: Throwable): Unit = ()
  def suspend(): Unit = ()
  def restart(cause: Throwable): Unit = ()
  def stop(): Unit = ()
  @deprecated("Use context.watch(actor) and receive Terminated(actor)", "2.2") override def isTerminated = false

  def sendSystemMessage(message: SystemMessage): Unit = ()

  def getParent: InternalActorRef = parent

  override def provider: ActorRefProvider = system.asInstanceOf[ActorSystemImpl].provider

  def getChild(name: Iterator[String]): InternalActorRef =
    Actor.noSender

  def isLocal: Boolean = true
}
