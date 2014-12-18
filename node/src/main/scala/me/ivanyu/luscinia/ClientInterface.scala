package me.ivanyu.luscinia

import spray.http.StatusCodes

import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, Props}
import akka.io.IO
import akka.pattern.ask
import me.ivanyu.luscinia.entities.ClientEndpoint
import spray.can.Http
import spray.routing._

import scala.util.{Failure, Success}

object ClientInterface {
  sealed trait ClientInterfaceMessage
  case class GetValue(key: String) extends ClientInterfaceMessage

  case class SetValue(key: String, value: String) extends ClientInterfaceMessage
  sealed trait SetValueResult extends ClientInterfaceMessage
  case object SetValueAck extends SetValueResult
  case object SetValueFailed extends SetValueResult

  case class DeleteValue(key: String) extends ClientInterfaceMessage
  sealed trait DeleteValueResult extends ClientInterfaceMessage
  case class DeleteValueAck(deletedValue: String) extends DeleteValueResult
  case object DeleteValueFailed extends DeleteValueResult

  def props(clientEndpoint: ClientEndpoint): Props =
    Props(classOf[ClientInterface], clientEndpoint)
}

class ClientInterface(endpoint: ClientEndpoint)
    extends HttpServiceActor with ActorLogging {

  import ClientInterface._

  override def preStart(): Unit = {
    IO(Http)(context.system) ! Http.Bind(self,
      interface = endpoint.host, port = endpoint.port)
  }

  private val askTimeout = 1.second

  private implicit val ec = context.dispatcher

  // TODO test client interface
  // TODO rewrite client interface
  override def receive: Actor.Receive = runRoute {
    path("v" / Segment) { key =>
      get { ctx =>
        val f = ask(context.parent, GetValue(key))(askTimeout).mapTo[Option[String]]
        f.onComplete {
          case Success(Some(value)) => ctx.complete(value)
          case Success(None) => ctx.complete(StatusCodes.NotFound)
          case Failure(_) => ctx.complete(StatusCodes.InternalServerError)
        }
      }~
      put { ctx =>
        val value = ctx.request.entity.asString
        val f = ask(context.parent, SetValue(key, value)).mapTo[SetValueResult]
        f.onComplete {
          case Success(`SetValueAck`) => ctx.complete("")
          case _ => ctx.complete(StatusCodes.InternalServerError)
        }
      }~
      delete { ctx =>
        val f = ask(context.parent, DeleteValue(key)).mapTo[DeleteValueResult]
        f.onComplete {
          case Success(DeleteValueAck(deletedValue)) => ctx.complete("") // TODO return deleted value
          case _ => ctx.complete(StatusCodes.InternalServerError)
        }
      }
    }
  }
}
