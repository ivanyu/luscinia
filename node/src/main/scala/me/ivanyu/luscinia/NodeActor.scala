package me.ivanyu.luscinia

import akka.actor._
import me.ivanyu.luscinia.MonitoringInterface.{MonitoringMessage}
import me.ivanyu.luscinia.entities._

object NodeActor {
  private sealed trait SchedulerMessage
  // Notification of election timeout
  private case object ElectionTick extends SchedulerMessage
  // Notification of RequestVoteRPCResend timeout
  private case object RequestVoteRPCResend extends SchedulerMessage
  // Notification of heartbeat timeout
  private case object HeartbeatTick extends SchedulerMessage


  // Node's states

  sealed trait FSMState
  case object Follower extends FSMState
  case object Candidate extends FSMState
  case object Leader extends FSMState

  /**
   * FSM data.
   */
  sealed trait FSMData {
    // Latest term the node has seen
    val currentTerm: Int
  }

  /**
   * FSM data for the Follower state
   * @param currentTerm latest term the node has seen
   */
  sealed case class FollowerData(currentTerm: Int) extends FSMData

  /**
   * FSM data for the Candidate state
   * @param currentTerm latest term the node has seen
   * @param requestVoteResultPending nodes we're waiting a response on RequestVote RPC from
   * @param votedForMe nodes voted for the candidate
   */
  sealed case class CandidateData(
    currentTerm: Int,
    requestVoteResultPending: Set[Node],
    votedForMe: Set[Node]
  ) extends FSMData

  /**
   * FSM data for the Leader state
   * @param currentTerm latest term the node has seen
   */
  sealed case class LeaderData(currentTerm: Int) extends FSMData


  def props(thisNode: Node,
            peers: Seq[Node],
            clusterInterfaceProps: Props,
            monitoringInterfaceProps: Props,
            electionTimeout: ElectionTimeout,
            rpcResendTimeout: RPCResendTimeout): Props = {
    Props(classOf[NodeActor], thisNode, peers,
      clusterInterfaceProps, monitoringInterfaceProps,
      electionTimeout, rpcResendTimeout)
  }
}

class NodeActor(val thisNode: Node,
                val peers: Seq[Node],
                val clusterInterfaceProps: Props,
                val monitoringInterfaceProps: Props,
                val electionTimeout: ElectionTimeout,
                val rpcResendTimeout: RPCResendTimeout)
    extends FSM[NodeActor.FSMState, NodeActor.FSMData] with ActorLogging {

  import me.ivanyu.luscinia.ClusterInterface._
  import me.ivanyu.luscinia.NodeActor._

import scala.concurrent.duration._

  val electionTimerName = "ElectionTimer"
  val resendRequestVoteTimerName = "ResendRequestVote"
  val heartbeatTimerName = "Heartbeat"

  private val clusterInterface = context.actorOf(clusterInterfaceProps, "cluster-interface")
  private val monitoringInterface = context.actorOf(monitoringInterfaceProps, "monitoring-interface")

  private val heartbeatTimeout = 50.millis

  // Operation log
  val opLog = Vector[LogEntry](LogEntry(0, EmptyOperation))

  // Index of highest log entry known to be commited
  var commitIndex = 0
  // Index of highest log entry applied to state machine
  var lastApplied = 0

  // Election majority
  val majority = Math.ceil((peers.length + 1) / 2.0).toInt

  override def preStart(): Unit = {
    scheduleElection()
    sendToMonitoring("Node started")
  }

  private def scheduleElection(): Unit = {
    setTimer(electionTimerName, ElectionTick, electionTimeout.random)
  }

  startWith(Follower, FollowerData(0))

  when(Follower) {
    case Event(ElectionTick, d: FollowerData) =>
      goto(Candidate) using CandidateData(d.currentTerm + 1, peers.toSet, Set(thisNode))
  }

  when(Candidate) {
    // RequestVote response from one of the peers
    case Event(RequestVoteResponse(responseTerm, voteGranted, sender, _), d: CandidateData) =>
      // If discovers higher/equal term, become a Follower
      if (responseTerm >= d.currentTerm)
        goto(Follower) using FollowerData(responseTerm)

      // If it's the last vote to get the majority, become the leader
      // Pay attention to double-senders
      else if (!d.votedForMe.contains(sender) && voteGranted && d.votedForMe.size + 1 >= majority)
        goto(Leader) using LeaderData(d.currentTerm)

      else {
        val newData = d.copy(
          requestVoteResultPending = d.requestVoteResultPending - sender,
          votedForMe = if (voteGranted) d.votedForMe + sender else d.votedForMe)
        setTimer(resendRequestVoteTimerName, RequestVoteRPCResend, rpcResendTimeout.timeout.millis)
        stay using newData
      }

    // Notification to resend RequestVote to peer that haven't answered yet
    case Event(RequestVoteRPCResend, CandidateData(currentTerm, pending, _)) =>
      pending.foreach { p =>
        clusterInterface !
          RequestVote(currentTerm, opLog.length - 1, opLog.last.term, thisNode, p)
      }
      setTimer(resendRequestVoteTimerName, RequestVoteRPCResend, rpcResendTimeout.timeout.millis)
      stay

    // Notification of election timeout during election => haven't got the majority, restart the election
    case Event(ElectionTick, d: CandidateData) =>
      goto(Candidate) using CandidateData(d.currentTerm + 1, peers.toSet, Set(thisNode))

    case Event(AppendEntries(leaderTerm, _, _, _, _, _, _), _) =>
      goto(Follower) using FollowerData(leaderTerm)
  }

  onTransition {
    case _ -> Candidate =>
      sendToMonitoring("Became Candidate")

      (nextStateData: @unchecked) match {
        case CandidateData(term, pending, _) =>
          pending.foreach { p =>
            clusterInterface !
              RequestVote(term, opLog.length - 1, opLog.last.term, thisNode, p)
          }
      }
      setTimer(electionTimerName, ElectionTick, electionTimeout.random)
      setTimer(resendRequestVoteTimerName, RequestVoteRPCResend, rpcResendTimeout.timeout.millis)

    case _ -> Leader =>
      sendToMonitoring("Became Leader")

      (nextStateData: @unchecked) match {
        case LeaderData(term) =>
          sendHeartbeat(term)
          setTimer(heartbeatTimerName, HeartbeatTick, heartbeatTimeout)
      }

    case _ -> Follower =>
      sendToMonitoring("Became Follower")
  }

  when(Leader) {
    // Notification to heartbeat
    case Event(HeartbeatTick, LeaderData(currentTerm)) =>
      sendHeartbeat(currentTerm)
      setTimer(heartbeatTimerName, HeartbeatTick, heartbeatTimeout)
      stay
  }

  private def sendHeartbeat(term: Int): Unit = {
    peers.foreach { p =>
      // TODO prevLogIndex etc.
      clusterInterface ! AppendEntries(term, 0, 0, List.empty, 0, thisNode, p)
    }
  }

  whenUnhandled {
    case Event(e, s) =>
      log.warning("Received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }

  initialize()

  private def sendToMonitoring(msg: String): Unit = {
    monitoringInterface ! MonitoringMessage(msg)
  }

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
