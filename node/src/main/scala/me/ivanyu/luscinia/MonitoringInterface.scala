package me.ivanyu.luscinia

import java.util.Date

import akka.actor._
import akka.io.IO
import me.ivanyu.luscinia.MonitoringInterface.MonitoringMessage
import me.ivanyu.luscinia.entities.MonitoringEndpoint
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket.{UpgradedToWebSocket, WebSocketServerWorker}
import spray.can.websocket.frame.TextFrame
import spray.routing.HttpServiceActor

object MonitoringInterface {
  sealed case class MonitoringMessage(msg: String, timestamp: Date = new Date())

  def props(endpoint: MonitoringEndpoint): Props =
    Props(classOf[MonitoringInterface], endpoint)
}

class MonitoringInterface(endpoint: MonitoringEndpoint)
    extends Actor with ActorLogging {

  private var messages: List[MonitoringMessage] = Nil

  private class Worker (val serverConnection: ActorRef, initialMessages: List[MonitoringMessage]) extends HttpServiceActor
      with WebSocketServerWorker with ActorLogging {

    private var _initialMessages: Option[List[MonitoringMessage]] = Some(initialMessages)

    override def businessLogic: Receive = {
      case UpgradedToWebSocket =>
        // Send initial bunch of messages
        _initialMessages.map { msgs => msgs.foreach(m => send(TextFrame(m.msg))) }
        _initialMessages = None

      // Messages from the parent
      case MonitoringMessage(msg, date) =>
        send(TextFrame(msg))
    }
  }

  override def preStart(): Unit = {
    IO(UHttp)(context.system) !
      Http.Bind(self, interface = endpoint.host, port = endpoint.port)
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
