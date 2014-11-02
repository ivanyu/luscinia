import akka.actor.{Props, ActorSystem}

object Node extends App {
  val system = ActorSystem("luscinia-node")
  system.actorOf(Props[NodeActor], name = "node")
}
