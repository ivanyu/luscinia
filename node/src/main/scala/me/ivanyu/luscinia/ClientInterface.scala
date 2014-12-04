package me.ivanyu.luscinia

import akka.actor.{ActorLogging, Props}
import akka.io.IO
import me.ivanyu.luscinia.entities.ClientEndpoint
import spray.can.Http
import spray.routing._

object ClientInterface {
  def props(clientEndpoint: ClientEndpoint): Props =
    Props(classOf[ClientInterface], clientEndpoint)
}

class ClientInterface(endpoint: ClientEndpoint)
    extends HttpServiceActor with ActorLogging {

  override def preStart(): Unit = {
    IO(Http)(context.system) ! Http.Bind(self,
      interface = endpoint.host, port = endpoint.port)
  }

  private implicit val ec = context.dispatcher

  override def receive = {
    case _ =>
  }

  /*
    override def receive: Actor.Receive = runRoute {
      path("v" / Segment) { key =>
        get { ctx =>
          StatusCodes.InternalServerError
        }
        get { ctx =>
          implicit val timeout: Timeout = 1.second
          val f = ask(context.parent, NodeActor.GetValue(key)).mapTo[Option[String]]
          f.onComplete {
            case Success(Some(value)) => ctx.complete(value)
            case Success(None) => ctx.complete(StatusCodes.NotFound)
            case Failure(_) => ctx.complete(StatusCodes.InternalServerError)
          }
        } ~
        put { ctx =>
          implicit val timeout: Timeout = 1.second
          val value = ctx.request.entity.asString
          val f = ask(context.parent, NodeActor.SetValue(key, value)).mapTo[NodeActor.OperationResult]
          f.onComplete {
            case Success(NodeActor.OperationAck) => ctx.complete("")
            case _ => ctx.complete(StatusCodes.InternalServerError)
          }
        } ~
        delete { ctx =>
          implicit val timeout: Timeout = 1.second
          val f = ask(context.parent, NodeActor.DeleteValue(key)).mapTo[NodeActor.OperationResult]
          f.onComplete {
            case Success(NodeActor.OperationAck) => ctx.complete("")
            case _ => ctx.complete(StatusCodes.InternalServerError)
          }
        }
    }
  }
  */
}
