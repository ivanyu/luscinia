package me.ivanyu.luscinia

import akka.testkit.{TestFSMRef, TestProbe}
import me.ivanyu.luscinia.ClusterInterface.RequestVoteResponse
import me.ivanyu.luscinia.entities.{ClientEndpoint, ClusterEndpoint, Node}

import scala.concurrent.duration._

class CandidateTest extends TestBase {
  private val node1 = Node("node1",
    ClusterEndpoint("localhost", 8091),
    ClientEndpoint("localhost", 8081))
  private val node2 = Node("node2",
    ClusterEndpoint("localhost", 8092),
    ClientEndpoint("localhost", 8082))
  private val node3 = Node("node3",
    ClusterEndpoint("localhost", 8093),
    ClientEndpoint("localhost", 8083))
  private val otherNodes = List(node2, node3)

  test("Case 1: must resend RequestVote only to nodes that haven't answered with RequestVoteResponse") {
    val clusterInterfaceProbe = TestProbe()
    val clusterInterfaceProbeProps = TestTools.probeProps(clusterInterfaceProbe)
    val node = TestFSMRef(new NodeActor(node1, otherNodes, clusterInterfaceProbeProps,
      electionTimeout, rpcResendTimeout))

    val initiallySentRPC = clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max + timingEpsilon) milliseconds,
      classOf[ClusterInterface.RequestVote], classOf[ClusterInterface.RequestVote])
    val rpcForNode2 = initiallySentRPC.filter {
      case ClusterInterface.RequestVote(_, _, _, _, receiver) if receiver == node2 => true
      case _ => false
    }.head

    // node3 has responded, node2 hasn't
    clusterInterfaceProbe.send(node, RequestVoteResponse(0, voteGranted = false, node3))

    // The candidate should resend RequestVote for node2
    clusterInterfaceProbe.expectMsgPF((rpcResendTimeout.timeout * 2 + timingEpsilon) milliseconds) {
      case x: ClusterInterface.RequestVote if x == rpcForNode2 => true
    }
  }
}
