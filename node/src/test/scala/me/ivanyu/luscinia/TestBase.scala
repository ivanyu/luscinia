package me.ivanyu.luscinia

import akka.actor.ActorSystem
import akka.testkit.{TestProbe, TestFSMRef, ImplicitSender, TestKit}
import me.ivanyu.luscinia.entities._
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}

abstract class TestBase extends TestKit(ActorSystem("Test"))
    with FunSuiteLike
//    with FlatSpecLike
//    with WordSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ImplicitSender {

  protected val node1 = Node("node1",
    ClusterEndpoint("localhost", 8091),
    ClientEndpoint("localhost", 8071),
    MonitoringEndpoint("localhost", 8081))
  protected val node2 = Node("node2",
    ClusterEndpoint("localhost", 8092),
    ClientEndpoint("localhost", 8072),
    MonitoringEndpoint("localhost", 8082))
  protected val node3 = Node("node3",
    ClusterEndpoint("localhost", 8093),
    ClientEndpoint("localhost", 8073),
    MonitoringEndpoint("localhost", 8083))
  protected val node4 = Node("node4",
    ClusterEndpoint("localhost", 8094),
    ClientEndpoint("localhost", 8074),
    MonitoringEndpoint("localhost", 8084))
  protected val node5 = Node("node5",
    ClusterEndpoint("localhost", 8095),
    ClientEndpoint("localhost", 8075),
    MonitoringEndpoint("localhost", 8085))
  protected val smallPeerList = List(node2, node3)
  protected val largePeerList = List(node2, node3, node4, node5)

  protected val electionTimeout = ElectionTimeout(150, 300)
  protected val rpcResendTimeout = RPCResendTimeout(60)

  protected val emptyLog = Vector(LogEntry(Term.start, EmptyOperation))

  protected val timingEpsilon = 30

  override protected def afterAll(): Unit = system.terminate()

  protected def init(peers: List[Node]):
      (TestFSMRef[NodeActor.FSMState, NodeActor.FSMData, NodeActor], TestProbe, TestProbe) = {
    val clusterInterfaceProbe = TestProbe()
    val clusterInterfaceProbeProps = TestTools.probeProps(clusterInterfaceProbe)
    val monitoringInterfaceProbe = TestProbe()
    val monitoringInterfaceProbeProps = TestTools.probeProps(monitoringInterfaceProbe)
    val node = TestFSMRef(new NodeActor(node1, peers, clusterInterfaceProbeProps, monitoringInterfaceProbeProps,
      electionTimeout, rpcResendTimeout))
    (node, clusterInterfaceProbe, monitoringInterfaceProbe)
  }
}
