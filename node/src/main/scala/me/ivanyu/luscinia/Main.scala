package me.ivanyu.luscinia

import akka.actor.ActorSystem
import me.ivanyu.luscinia.entities._

object Main extends App {
  val system = ActorSystem("luscinia-node")

  // TODO read config and args
  // TODO do some integration tests

  val thisNode = Node("node1",
    ClusterEndpoint("localhost", 8091),
    ClientEndpoint("localhost", 8071),
    MonitoringEndpoint("localhost", 8081))
  val peers: Seq[Node] = Nil
  val clusterInterfaceProps = ClusterInterface.props(thisNode, peers)
  val monitoringInterfaceProps = MonitoringInterface.props(thisNode.monitoringEndpoint)
  val electionTimeout = ElectionTimeout(150, 300)
  val rpcResendTimeout = RPCResendTimeout(60)

  val nodeActorProps = NodeActor.props(
    thisNode, peers, clusterInterfaceProps, monitoringInterfaceProps, electionTimeout, rpcResendTimeout)
  system.actorOf(nodeActorProps, "node")
}

