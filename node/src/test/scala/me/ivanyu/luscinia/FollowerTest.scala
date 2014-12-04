import scala.concurrent.duration._
import akka.actor.Kill
import me.ivanyu.luscinia.ClusterInterface.AppendEntries
import me.ivanyu.luscinia.{ClusterInterface, NodeActor, TestBase}

class FollowerTest extends TestBase {
  import system.dispatcher

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
    val (node, clusterInterfaceProbe, monitoringInterfaceProbe) = init(smallPeerList)

    val delta = electionTimeout.min / 2
    val cnt = 10
    (delta to (cnt * delta, delta)).foreach { int =>
      system.scheduler.scheduleOnce(int.millis, new Runnable {
        override def run(): Unit = clusterInterfaceProbe.send(node, AppendEntries(2, 0, 0, Nil, 0, node2, node1))
      })
    }

    clusterInterfaceProbe.expectNoMsg(((cnt + 2) * delta).millis)

    assert(node.stateName == NodeActor.Follower)

    node ! Kill
  }

}
