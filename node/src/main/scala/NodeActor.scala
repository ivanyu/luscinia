import akka.actor.{Actor, ActorLogging, Props}

import scala.collection.immutable.HashMap

object NodeActor {
  sealed trait NodeOperation
  case class GetValue(key: String) extends NodeOperation
  case class SetValue(key: String, value: String) extends NodeOperation
  case class DeleteValue(key: String) extends NodeOperation

  sealed trait OperationResult
  case object OperationAck extends OperationResult
  case object OperationFailure extends OperationResult
}

class NodeActor extends Actor with ActorLogging {
  import NodeActor._

  private val clientApi = context.actorOf(Props[ClientApiActor], "node-client-api")

  private var storage = new HashMap[String, String]

  override def receive: Receive = {
    case GetValue(key) =>
      val value = storage.get(key)
      log.info(s"Get value request, key '$key', value '$value'")
      sender ! value

    case SetValue(key, value) =>
      log.info(s"Set value request, key '$key', value '$value'")
      storage += (key -> value)
      sender ! OperationAck

    case DeleteValue(key) =>
      log.info(s"Delete value request, key '$key'")
      storage -= key
      sender ! OperationAck

    case _ =>
  }
}
