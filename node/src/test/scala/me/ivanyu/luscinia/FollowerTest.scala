import me.ivanyu.luscinia.ClusterInterface.{RequestVoteResponse, RequestVote}
import me.ivanyu.luscinia.NodeActor.{Candidate, Follower, FollowerData}
import me.ivanyu.luscinia.entities.Term
import me.ivanyu.luscinia.{NodeActor, TestBase}

import scala.concurrent.duration._

class FollowerTest extends TestBase {

  test("Case 1: reaction to no cluster RPCs") {
    val (node, clusterInterfaceProbe, monitoringInterfaceProbe) = init(smallPeerList)

    // Must send RequestVote RPC to peers
    val sent = clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max + timingEpsilon).milliseconds,
      List.fill(smallPeerList.size)(classOf[RequestVote]):_*)
    assert(sent.length == smallPeerList.length)

    val receivers = sent.map(_.receiver).toSet
    assert((smallPeerList.toSet -- receivers).isEmpty)

    // The state must be Candidate
    assert(node.stateName == NodeActor.Candidate)

    node.stop()
  }

  test("Case 2: reaction to RequestVote RPC with lower term") {
    val (node, clusterInterfaceProbe, monitoringInterfaceProbe) = init(smallPeerList)

    // The node have the higher term
    node.setState(Follower, FollowerData(Term(5), emptyLog, None))

    // Receives RPC with lower term -> response with false and continue election countdown
    clusterInterfaceProbe.send(node, RequestVote(Term(1), 0, Term(0), node2, node1))

    // Expecting denial of vote
    clusterInterfaceProbe.expectMsgPF() {
      case RequestVoteResponse(term, voteGranted, rpcSender, receiver) =>
        term.t == 1 && !voteGranted && rpcSender == node1 && receiver == node2
    }

    // Expecting node1's RequestVote
    val sent = clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max + timingEpsilon).milliseconds,
      List.fill(smallPeerList.size)(classOf[RequestVote]):_*)
    assert(sent.length == smallPeerList.length)

    assert(node.stateName == Candidate)

    node.stop()
  }

  test("Case 3: equal term + voted for another peer") {
    val (node, clusterInterfaceProbe, monitoringInterfaceProbe) = init(smallPeerList)

    val t = Term(5)

    // Already voted for another peer
    node.setState(Follower, FollowerData(t, emptyLog, Some(node3)))

    clusterInterfaceProbe.send(node, RequestVote(t, 0, Term.start, node2, node1))

    // Expecting denial of vote
    clusterInterfaceProbe.expectMsgPF() {
      case RequestVoteResponse(term, voteGranted, rpcSender, receiver) =>
        term.t == 1 && !voteGranted && rpcSender == node1 && receiver == node2
    }

    assert(node.stateName == Follower)
    assert(node.stateData == FollowerData(t, emptyLog, Some(node3)))
  }

/*
  test("Case 4: higher/equal term + hasn't voted for another peer + last log term smaller") {
    val (node, clusterInterfaceProbe, monitoringInterfaceProbe) = init(smallPeerList)

    val t = Term(5)

    // Hasn't voted for another peer
    node.setState(Follower, FollowerData(t, emptyLog, None))

    clusterInterfaceProbe.send(node, RequestVote(t, 0, Term(4), node2, node1))

    // Expecting denial of vote
    clusterInterfaceProbe.expectMsgPF() {
      case RequestVoteResponse(term, voteGranted, rpcSender, receiver) =>
        term.t == 1 && !voteGranted && rpcSender == node1 && receiver == node2
    }

    assert(node.stateName == Follower)
    // Should accept higher term
//    assert(node.stateData == FollowerData(t2, emptyLog, Some(node3)))
  }
*/
}
