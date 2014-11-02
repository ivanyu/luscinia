import akka.actor.{Actor, ActorLogging}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import spray.can.Http
import spray.http.StatusCodes
import spray.routing._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

class ClientApiActor extends HttpServiceActor with ActorLogging {
  override def preStart(): Unit = {
    IO(Http)(context.system) ! Http.Bind(self, interface = "localhost", port = 8080)
  }

  private implicit val ec = context.dispatcher

  override def receive: Actor.Receive = runRoute {
    path("v" / Segment) { key =>
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
}
