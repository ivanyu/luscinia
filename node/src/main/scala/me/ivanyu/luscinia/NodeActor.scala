package me.ivanyu.luscinia

import akka.actor._
import me.ivanyu.luscinia.entities._

object NodeActor {
  private sealed trait SchedulerMessage
  // Notification of election timeout
  private case object ElectionTick extends SchedulerMessage
  // Notification of RequestVoteRPCResend timeout
  private case object RequestVoteRPCResend extends SchedulerMessage

  // Node's states
  sealed trait FSMState
  case object Follower extends FSMState
  case object Candidate extends FSMState
  case object Leader extends FSMState

  // Node's data
  sealed trait FSMData {
    // Latest term the node has seen
    val currentTerm: Int
  }

  sealed case class FollowerData(currentTerm: Int = 0) extends FSMData {
    def toCandidateData(candidate: Node, nodes: Traversable[Node]): CandidateData =
      CandidateData(this.currentTerm + 1, nodes.toSet, Set(candidate))
  }

  sealed case class CandidateData(
    currentTerm: Int,
    // Nodes we're waiting a response on RequestVote RPC from
    requestVoteResultPending: Set[Node],
    // Nodes voted for the candidate
    votedForMe: Set[Node]
  ) extends FSMData


  def props(thisNode: Node, otherNodes: Traversable[Node],
            clusterInterfaceProps: Props,
            electionTimeout: ElectionTimeout,
            rpcResendTimeout: RPCResendTimeout): Props = {
    Props(classOf[NodeActor], thisNode, otherNodes,
      clusterInterfaceProps,
      electionTimeout, rpcResendTimeout)
  }
}

class NodeActor(val thisNode: Node,
                val otherNodes: Traversable[Node],
                val clusterInterfaceProps: Props,
                val electionTimeout: ElectionTimeout,
                val rpcResendTimeout: RPCResendTimeout)
    extends FSM[NodeActor.FSMState, NodeActor.FSMData] with ActorLogging {

  import scala.concurrent.duration._
  import me.ivanyu.luscinia.ClusterInterface._
  import me.ivanyu.luscinia.NodeActor._

  val electionTimerName = "ElectionTimer"
  val resendRequestVoteTimerName = "ResendRequestVote"

  private val clusterInterface = context.actorOf(clusterInterfaceProps)

  // Operation log
  val opLog = Vector[LogEntry](LogEntry(0, EmptyOperation))

  // Index of highest log entry known to be commited
  var commitIndex = 0
  // Index of highest log entry applied to state machine
  var lastApplied = 0


  override def preStart(): Unit = scheduleElection()

  private def scheduleElection(): Unit = {
    setTimer(electionTimerName, ElectionTick, electionTimeout.random)
  }

  startWith(Follower, new FollowerData)

  when(Follower) {
    case Event(ElectionTick, d: FollowerData) =>
      goto(Candidate) using d.toCandidateData(thisNode, otherNodes)
  }

  when(Candidate) {
    case Event(RequestVoteResponse(term, voteGranted, sender), d: CandidateData) =>
      stay using d.copy(requestVoteResultPending = d.requestVoteResultPending - sender)

    case Event(RequestVoteRPCResend, CandidateData(currentTerm, pending, _)) =>
      pending.foreach { n =>
        clusterInterface !
          RequestVote(currentTerm, opLog.length - 1, opLog.last.term, thisNode, n)
      }
      stay
  }

  when(Leader) (FSM.NullFunction)

  onTransition {
    case Follower -> Candidate =>
      (nextStateData: @unchecked) match {
        case CandidateData(currentTerm, pendingNodes, _) =>
          pendingNodes.foreach { n =>
            clusterInterface !
              RequestVote(currentTerm, opLog.length - 1, opLog.last.term, thisNode, n)
          }
      }
      setTimer(resendRequestVoteTimerName, RequestVoteRPCResend, rpcResendTimeout.timeout.millis)
  }

  whenUnhandled {
    case Event(e, s) =>
      log.warning("Received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }


  initialize()


/*  import context.dispatcher
  import me.ivanyu.luscinia.ClusterInterface._

  private val clusterInterface = context.actorOf(clusterInterfaceProps)

  private var electionTick: Option[Cancellable] = None

  // Operation log
  val opLog = Vector[LogEntry](LogEntry(0, EmptyOperation))

  // Latest term the node has seen
  var currentTerm = 0
  // Index of highest log entry known to be commited
  var commitIndex = 0
  // Index of highest log entry applied to state machine
  var lastApplied = 0

  var votedFor: Option[Node] = None

  override def preStart(): Unit = scheduleElection()

  private def scheduleElection(): Unit = {
    electionTick.map(_.cancel())
    electionTick = Some {
      context.system.scheduler.scheduleOnce(electionTimeout.random, self, ElectionTick)
    }
  }

  override def receive: Actor.Receive = followerBehavior

  private def followerBehavior: Receive = {
    case ElectionTick =>
      currentTerm += 1
      otherNodes.foreach { n =>
        clusterInterface !
          RequestVote(currentTerm, opLog.length - 1, opLog.last.term, thisNode, n)
      }
      context.become(candidateBehavior)
      //scheduleElection()

    case msg: RequestVote =>
      log.info(msg.toString)
      sender ! handleRequestVote(msg)

    case msg =>
      log.info(msg.toString)
  }

  private def handleRequestVote(msg: RequestVote): RequestVoteResult = {
    if (votedFor.nonEmpty || msg.term < currentTerm) {
      RequestVoteResult(currentTerm, voteGranted = false)
    } else {
      if (opLog.last.term > msg.lastLogTerm
          || opLog.length <= msg.lastLogIndex) {
        RequestVoteResult(currentTerm, voteGranted = false)
      } else {
        votedFor = Some(msg.candidate)
        RequestVoteResult(currentTerm, voteGranted = true)
      }
    }
  }

  private def candidateBehavior: Receive = {
    case _ =>
  }*/


/*
  private def valuesBehavior: Receive = {
    case GetValue(key) =>
      val value = storage.get(key)
      log.info(s"Get value request, key '$key', value '$value'")
      sender ! value

    case SetValue(key, value) =>
      log.info(s"Set value request, key '$key', value '$value'")
      storage += (key -> value)
      sender ! OperationAck

    case DeleteValue(key) =>
      log.info(s"Delete value request, key '$key'")
      storage -= key
      sender ! OperationAck

//    case x =>
//      log.info(x.toString)
  }
*/
}
