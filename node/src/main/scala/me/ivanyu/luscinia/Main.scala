package me.ivanyu.luscinia

import akka.actor.ActorSystem
import me.ivanyu.luscinia.entities._

object Main extends App {
  val system = ActorSystem("luscinia-node")

  val thisNode = Node("node1",
    ClusterEndpoint("localhost", 8091),
    ClientEndpoint("localhost", 8081))
  val otherNodes: List[Node] = Nil
  val clusterInterfaceProps = ClusterInterface.props(thisNode, otherNodes)
  val electionTimeout = ElectionTimeout(150, 300)
  val rpcResendTimeout = RPCResendTimeout(60)

  val nodeActorProps = NodeActor.props(
    thisNode, otherNodes, clusterInterfaceProps, electionTimeout, rpcResendTimeout)
  system.actorOf(nodeActorProps)

  //val x: ServerAddress = Address("", 1)
}
