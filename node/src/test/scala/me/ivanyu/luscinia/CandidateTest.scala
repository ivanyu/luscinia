package me.ivanyu.luscinia

import akka.testkit.{TestFSMRef, TestProbe}
import me.ivanyu.luscinia.ClusterInterface.{RequestVoteResponse, RequestVote, AppendEntries}
import me.ivanyu.luscinia.NodeActor.{Candidate, Follower, Leader}
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
  private val node4 = Node("node4",
    ClusterEndpoint("localhost", 8094),
    ClientEndpoint("localhost", 8084))
  private val node5 = Node("node5",
    ClusterEndpoint("localhost", 8095),
    ClientEndpoint("localhost", 8085))
  private val smallPeerList = List(node2, node3)
  private val largePeerList = List(node2, node3, node4, node5)

  private def init(peers: List[Node]): (TestFSMRef[NodeActor.FSMState, NodeActor.FSMData, NodeActor], TestProbe) = {
    val clusterInterfaceProbe = TestProbe()
    val clusterInterfaceProbeProps = TestTools.probeProps(clusterInterfaceProbe)
    val node = TestFSMRef(new NodeActor(node1, peers, clusterInterfaceProbeProps,
      electionTimeout, rpcResendTimeout))
    (node, clusterInterfaceProbe)
  }

  test("Case 1: must resend RequestVote only to nodes that haven't answered with RequestVoteResponse") {
    val (node, clusterInterfaceProbe) = init(smallPeerList)

    val initiallySentRPC = clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max + timingEpsilon).milliseconds,
      List.fill(smallPeerList.size)(classOf[RequestVote]):_*)
    val rpcForNode2 = initiallySentRPC.filter {
      case RequestVote(_, _, _, _, receiver) if receiver == node2 => true
      case _ => false
    }.head

    // node3 has responded, node2 hasn't
    clusterInterfaceProbe.send(node, ClusterInterface.RequestVoteResponse(10, voteGranted = false, node3, node1))

    // The candidate should resend RequestVote for node2
    clusterInterfaceProbe.expectMsgPF((rpcResendTimeout.timeout * 2 + timingEpsilon).milliseconds) {
      case x: RequestVote if x == rpcForNode2 => true
    }
  }

  test("Case 2: must restart election with new term if hasn't got the majority") {
    val (node, clusterInterfaceProbe) = init(smallPeerList)

    val initiallySentRPC = clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max + timingEpsilon).milliseconds,
      List.fill(smallPeerList.size)(classOf[RequestVote]):_*)
    val initialTerm = initiallySentRPC.head.term

    clusterInterfaceProbe.send(node, RequestVoteResponse(10, voteGranted = false, node2, node1))
    clusterInterfaceProbe.send(node, RequestVoteResponse(10, voteGranted = false, node3, node1))

    val secondarySentRPC = clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max * 2 + timingEpsilon).milliseconds,
      classOf[RequestVote], classOf[RequestVote])
    val secondaryTerm = secondarySentRPC.head.term

    println(secondarySentRPC)
    assert(secondarySentRPC.length == initiallySentRPC.length)
    assert(secondaryTerm == initialTerm + 1)
  }

  test("Case 3: must step back to the Follower state if received ReplicateLog RPC from the leader") {
    val (node, clusterInterfaceProbe) = init(smallPeerList)

    val initiallySentRPC = clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max + timingEpsilon).milliseconds,
      List.fill(smallPeerList.size)(classOf[RequestVote]):_*)
    val initialTerm = initiallySentRPC.head.term

    // RPC from the actual leader
    clusterInterfaceProbe.send(node, AppendEntries(10, 1, 10, List.empty, 0, node2, node1))

    assert(node.stateName == Follower)
  }

  test("Case 4: must adequately process RequestVote response doubles") {
    val (node, clusterInterfaceProbe) = init(largePeerList)

    val initiallySentRPC = clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max + timingEpsilon).milliseconds,
      List.fill(largePeerList.size)(classOf[RequestVote]):_*)

    // One vote for, but multiple times
    clusterInterfaceProbe.send(node, RequestVoteResponse(0, voteGranted = true, node2, node1))
    clusterInterfaceProbe.send(node, RequestVoteResponse(10, voteGranted = false, node3, node1))
    clusterInterfaceProbe.send(node, RequestVoteResponse(0, voteGranted = true, node2, node1))
    clusterInterfaceProbe.send(node, RequestVoteResponse(10, voteGranted = false, node4, node1))
    clusterInterfaceProbe.send(node, RequestVoteResponse(0, voteGranted = true, node2, node1))
    clusterInterfaceProbe.send(node, RequestVoteResponse(10, voteGranted = false, node5, node1))

    // Must remain candidate
    assert(node.stateName == Candidate)

    // Must restart the election
    clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max * 2 + timingEpsilon).milliseconds,
      classOf[RequestVote], classOf[RequestVote])
  }

  test("Case 5: Must become the Leader in case gains the majority") {
    val (node, clusterInterfaceProbe) = init(largePeerList)

    clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max + timingEpsilon).milliseconds,
      List.fill(largePeerList.size)(classOf[RequestVote]):_*)

    clusterInterfaceProbe.send(node, RequestVoteResponse(0, voteGranted = true, node2, node1))
    clusterInterfaceProbe.send(node, RequestVoteResponse(0, voteGranted = true, node3, node1))
    clusterInterfaceProbe.send(node, RequestVoteResponse(0, voteGranted = false, node4, node1))
    clusterInterfaceProbe.send(node, RequestVoteResponse(0, voteGranted = false, node5, node1))

    assert(node.stateName == Leader)
  }
}
