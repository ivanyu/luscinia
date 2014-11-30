import scala.concurrent.duration._
import akka.actor.Kill
import akka.testkit.{TestFSMRef, TestProbe}
import me.ivanyu.luscinia.{ClusterInterface, NodeActor, TestTools, TestBase}
import me.ivanyu.luscinia.entities.{ClientEndpoint, ClusterEndpoint, Node}

class FollowerTest extends TestBase {
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

  test("Case 1: must become a Candidate and RequestVote in ElectionTimeout after start if is receiving no cluster RPCs") {
    // Initialize
    val clusterInterfaceProbe = TestProbe()
    val clusterInterfaceProbeProps = TestTools.probeProps(clusterInterfaceProbe)
    val node = TestFSMRef(new NodeActor(node1, otherNodes, clusterInterfaceProbeProps,
      electionTimeout, rpcResendTimeout))

    // Must send two RequestVote
    val sent = clusterInterfaceProbe.expectMsgAllClassOf(
      (electionTimeout.max + timingEpsilon) milliseconds,
      classOf[ClusterInterface.RequestVote], classOf[ClusterInterface.RequestVote])
    assert(sent.length == otherNodes.length)

    val receivers = sent.map(_.receiver).toSet
    assert((otherNodes.toSet -- receivers).isEmpty)

    // The state must be Candidate
    assert(node.stateName == NodeActor.Candidate)

    node ! Kill
  }

  test("Case 2: must not become a Candidate and RequestVote in ElectionTimeout if receiving cluster RPCs") {
    // TODO implement
  }

}
