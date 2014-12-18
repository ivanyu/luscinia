package me.ivanyu.luscinia

import akka.actor.Kill
import me.ivanyu.luscinia.ClusterInterface.{AppendEntries, RequestVote, RequestVoteResponse}
import me.ivanyu.luscinia.NodeActor.{Candidate, Follower, Leader}
import me.ivanyu.luscinia.entities.Term

import scala.concurrent.duration._

class CandidateTest extends TestBase {
  test("Case 1: must resend RequestVote only to nodes that haven't answered with RequestVoteResponse") {
    val (node, clusterInterfaceProbe, monitoringInterfaceProbe) = init(smallPeerList)

    val initiallySentRPC = clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max + timingEpsilon).milliseconds,
      List.fill(smallPeerList.size)(classOf[RequestVote]):_*)
    val rpcForNode2 = initiallySentRPC.filter {
      case RequestVote(_, _, _, _, receiver) if receiver == node2 => true
      case _ => false
    }.head

    // node3 has responded, node2 hasn't
    clusterInterfaceProbe.send(node, ClusterInterface.RequestVoteResponse(Term(0), voteGranted = false, node3, node1))

    // The candidate should resend RequestVote for node2
    clusterInterfaceProbe.expectMsgPF((rpcResendTimeout.timeout * 2 + timingEpsilon).milliseconds) {
      case x: RequestVote if x == rpcForNode2 => true
    }

    node ! Kill
  }

  test("Case 2: must restart election with new term if hasn't got the majority") {
    val (node, clusterInterfaceProbe, monitoringInterfaceProbe) = init(smallPeerList)

    val initiallySentRPC = clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max + timingEpsilon).milliseconds,
      List.fill(smallPeerList.size)(classOf[RequestVote]):_*)
    val initialTerm = initiallySentRPC.head.term

    clusterInterfaceProbe.send(node, RequestVoteResponse(Term(0), voteGranted = false, node2, node1))
    clusterInterfaceProbe.send(node, RequestVoteResponse(Term(0), voteGranted = false, node3, node1))

    val secondarySentRPC = clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max * 2 + timingEpsilon).milliseconds,
      classOf[RequestVote], classOf[RequestVote])
    val secondaryTerm = secondarySentRPC.head.term

    println(secondarySentRPC)
    assert(secondarySentRPC.length == initiallySentRPC.length)
    assert(secondaryTerm == initialTerm.next)

    node ! Kill
  }

  test("Case 3: must step back to the Follower state if receives AppendEntries RPC from the leader") {
    val (node, clusterInterfaceProbe, monitoringInterfaceProbe) = init(smallPeerList)

    val initiallySentRPC = clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max + timingEpsilon).milliseconds,
      List.fill(smallPeerList.size)(classOf[RequestVote]):_*)
    val initialTerm = initiallySentRPC.head.term

    // RPC from the actual leader
    clusterInterfaceProbe.send(node, AppendEntries(Term(10), 1, Term(10), List.empty, 0, node2, node1))

    assert(node.stateName == Follower)

    node ! Kill
  }

  test("Case 4: Must step back to the Follower state if receives RequestVote response with higher/equal term") {
    val (node, clusterInterfaceProbe, monitoringInterfaceProbe) = init(smallPeerList)

    val initiallySentRPC = clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max + timingEpsilon).milliseconds,
      List.fill(smallPeerList.size)(classOf[RequestVote]):_*)
    initiallySentRPC.head.term

    clusterInterfaceProbe.send(node, RequestVoteResponse(Term(10), voteGranted = false, node2, node1))

    assert(node.stateName == Follower)

    node ! Kill
  }

  test("Case 5: must adequately process RequestVote response doubles") {
    val (node, clusterInterfaceProbe, monitoringInterfaceProbe) = init(largePeerList)

    clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max + timingEpsilon).milliseconds,
      List.fill(largePeerList.size)(classOf[RequestVote]):_*)

    // One vote for, but multiple times
    clusterInterfaceProbe.send(node, RequestVoteResponse(Term(0), voteGranted = true, node2, node1))
    clusterInterfaceProbe.send(node, RequestVoteResponse(Term(0), voteGranted = false, node3, node1))
    clusterInterfaceProbe.send(node, RequestVoteResponse(Term(0), voteGranted = true, node2, node1))
    clusterInterfaceProbe.send(node, RequestVoteResponse(Term(0), voteGranted = false, node4, node1))
    clusterInterfaceProbe.send(node, RequestVoteResponse(Term(0), voteGranted = true, node2, node1))
    clusterInterfaceProbe.send(node, RequestVoteResponse(Term(0), voteGranted = false, node5, node1))

    // Must remain candidate
    assert(node.stateName == Candidate)

    // Must restart the election
    clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max * 2 + timingEpsilon).milliseconds,
      classOf[RequestVote], classOf[RequestVote])

    node ! Kill
  }

  test("Case 6: Must become the Leader in case gains the majority and immediately send AppendEntries RPC to all the peers") {
    val (node, clusterInterfaceProbe, monitoringInterfaceProbe) = init(largePeerList)

    clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max + timingEpsilon).milliseconds,
      List.fill(largePeerList.size)(classOf[RequestVote]):_*)

    clusterInterfaceProbe.send(node, RequestVoteResponse(Term(0), voteGranted = true, node2, node1))
    clusterInterfaceProbe.send(node, RequestVoteResponse(Term(0), voteGranted = true, node3, node1))
    clusterInterfaceProbe.send(node, RequestVoteResponse(Term(0), voteGranted = false, node4, node1))
    clusterInterfaceProbe.send(node, RequestVoteResponse(Term(0), voteGranted = false, node5, node1))

    assert(node.stateName == Leader)

    clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max + timingEpsilon).milliseconds,
      List.fill(largePeerList.size)(classOf[AppendEntries]):_*)

    node ! Kill
  }

  test("Case 7: Must elect himself in solitude") {
    val (node, clusterInterfaceProbe, monitoringInterfaceProbe) = init(Nil)

    clusterInterfaceProbe.expectNoMsg((electionTimeout.max + timingEpsilon).milliseconds)

    assert(node.stateName == Leader)

    node ! Kill
  }
}
