import akka.actor.Kill
import me.ivanyu.luscinia.{ClusterInterface, NodeActor, TestBase}

import scala.concurrent.duration._

class FollowerTest extends TestBase {
  test("Case 1: must become a Candidate and RequestVote in ElectionTimeout after start if is receiving no cluster RPCs") {
    val (node, clusterInterfaceProbe, monitoringInterfaceProbe) = init(smallPeerList)

    // Must send two RequestVote
    val sent = clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max + timingEpsilon).milliseconds,
      classOf[ClusterInterface.RequestVote], classOf[ClusterInterface.RequestVote])
    assert(sent.length == smallPeerList.length)

    val receivers = sent.map(_.receiver).toSet
    assert((smallPeerList.toSet -- receivers).isEmpty)

    // The state must be Candidate
    assert(node.stateName == NodeActor.Candidate)

    node ! Kill
  }

  test("Case 2: must not become a Candidate and RequestVote in ElectionTimeout if receiving cluster RPCs") {
    // TODO implement
  }

}
