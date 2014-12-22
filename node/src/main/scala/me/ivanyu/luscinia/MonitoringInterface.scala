package me.ivanyu.luscinia

import java.util.Date

import akka.actor._
import akka.io.IO
import me.ivanyu.luscinia.entities.MonitoringEndpoint
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket.frame.TextFrame
import spray.can.websocket.{UpgradedToWebSocket, WebSocketServerWorker}
import spray.routing.HttpServiceActor

object MonitoringInterface {
  sealed case class MonitoringMessage(msg: String, timestamp: Date = new Date())

  def props(endpoint: MonitoringEndpoint): Props =
    Props(classOf[MonitoringInterface], endpoint)

  private def jsonifyMessage(msg: MonitoringMessage): String = {
    s"""{"ts":"${msg.timestamp}", "msg": "${msg.msg}"}"""
  }
}

class MonitoringInterface(endpoint: MonitoringEndpoint)
    extends Actor with ActorLogging {

  import me.ivanyu.luscinia.MonitoringInterface._

  private var messages: List[MonitoringMessage] = Nil

  private class Worker (val serverConnection: ActorRef, initialMessages: List[MonitoringMessage])
      extends HttpServiceActor with WebSocketServerWorker with ActorLogging {

    private var _initialMessages: Option[List[MonitoringMessage]] = Some(initialMessages)

    private val sendMessage = jsonifyMessage _ andThen TextFrame.apply andThen send

    override def businessLogic: Receive = {
      case UpgradedToWebSocket =>
        // Send initial bunch of messages
        if (_initialMessages.nonEmpty)
          _initialMessages.get.foreach(sendMessage)
        _initialMessages = None

      // Messages from the parent
      case m: MonitoringMessage => sendMessage(m)
    }
  }

  override def preStart(): Unit = {
    IO(UHttp)(context.system) !
      Http.Bind(self, interface = endpoint.address, port = endpoint.port)
  }

  override def receive: Receive = {
    // Messages from the node
    case m: MonitoringMessage =>
      messages = m :: messages
      context.children foreach { _ ! m }

    case _: Http.Connected =>
      log.info("Client connected")
      val serverConnection = sender()
      // `this` is important because Worker is an inner class
      val worker = context.actorOf(Props(classOf[Worker], this, serverConnection, messages))
      serverConnection ! Http.Register(worker)
  }
}
