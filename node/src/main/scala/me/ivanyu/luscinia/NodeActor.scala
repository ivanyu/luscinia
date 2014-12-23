package me.ivanyu.luscinia

import java.util.Calendar

import akka.actor._
import me.ivanyu.luscinia.MonitoringInterface.MonitoringMessage
import me.ivanyu.luscinia.entities._

object NodeActor {
  private sealed trait SchedulerMessage
  // Notification of election timeout
  private case object ElectionTick extends SchedulerMessage
  // Notification of RequestVoteRPCResendTick timeout
  private case object RequestVoteRPCResendTick extends SchedulerMessage
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
    val currentTerm: Term
    // Operation log
    val opLog: Vector[LogEntry]
  }

  object FSMData {
    def unapply(d: FSMData): Option[(Term, Vector[LogEntry])] = {
      if (d != null) Some(d.currentTerm, d.opLog)
      else None
    }
  }

  /**
   * FSM data for the Follower state
   * @param currentTerm latest term the node has seen
   * @param opLog operation log
   * @param votedFor node a follower voted for
   */
  sealed case class FollowerData(currentTerm: Term,
                                 opLog: Vector[LogEntry],
                                 votedFor: Option[Node]) extends FSMData

  /**
   * FSM data for the Candidate state
   * @param currentTerm latest term the node has seen
   * @param opLog operation log
   * @param requestVoteResultPending nodes we're waiting a response on RequestVote RPC from
   * @param votedForMe nodes voted for the candidate
   */
  sealed case class CandidateData(currentTerm: Term,
                                  opLog: Vector[LogEntry],
                                  requestVoteResultPending: Set[Node],
                                  votedForMe: Set[Node]) extends FSMData

  /**
   * FSM data for the Leader state
   * @param currentTerm latest term the node has seen
   * @param opLog operation log
   */
  sealed case class LeaderData(currentTerm: Term,
                               opLog: Vector[LogEntry]) extends FSMData


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

  // TODO Actually send necessary information to monitoring

  import me.ivanyu.luscinia.ClusterInterface._
  import me.ivanyu.luscinia.NodeActor._

  import scala.concurrent.duration._

  val electionTimerName = "ElectionTimer"
  val resendRequestVoteTimerName = "ResendRequestVote"
  val heartbeatTimerName = "Heartbeat"

  private val calendar = Calendar.getInstance()

  private val clusterInterface = context.actorOf(clusterInterfaceProps, "cluster-interface")
  private val monitoringInterface = context.actorOf(monitoringInterfaceProps, "monitoring-interface")

  // TODO move heartbeatTimeout to config
  private val heartbeatTimeout = 50.millis

  // Index of highest log entry known to be commited
  var commitIndex = -1
  // Index of highest log entry applied to state machine
  var lastApplied = -1

  var lastRPCFromLeader: Option[Long] = None

  // Election majority
  val majority = Math.ceil((peers.length + 1) / 2.0).toInt

  override def preStart(): Unit = {
    scheduleElection()
    sendToMonitoring("Node started")
  }

  private def scheduleElection(): Unit = {
    setTimer(electionTimerName, ElectionTick, electionTimeout.random)
  }

  startWith(Follower, FollowerData(currentTerm = Term.start, opLog = Vector[LogEntry](), votedFor = None))

  // Behavior shared by all node states
  val commonBehavior: PartialFunction[Event, State] = {
    // AppendEntries RPC
    case Event(
        AppendEntries(term, prevLogIndex, prevLogTerm, entries, leaderCommit, rpcSender, _),
        FSMData(currentTerm, opLog)) =>
      if (term < currentTerm) {
        clusterInterface ! AppendEntriesResponse(currentTerm, success = false, thisNode, rpcSender)
        stay()
      } else {
        lastRPCFromLeader = Some(calendar.getTimeInMillis)
        resetElectionTimer()

        // We'll accept Leader's term later
        val newTerm = term
        val (success, newOpLog) =
          if (prevLogIndex < opLog.length && opLog(prevLogIndex).term != prevLogTerm)
            (false, opLog)
          else
            // Throw away (possibly zero) invalid items and append new ones
            (true, opLog.take(prevLogIndex + 1) ++ entries.map(LogEntry(term, _)))

        // Commit index
        if (leaderCommit > commitIndex)
          commitIndex = Math.min(leaderCommit, opLog.length - 1)

        clusterInterface ! AppendEntriesResponse(newTerm, success, thisNode, rpcSender)

        val newFollowerData = FollowerData(newTerm, opLog = newOpLog, votedFor = None)
        if (term > currentTerm && (stateName == Leader || stateName == Candidate))
          goto(Follower) using newFollowerData
        else
          stay() using newFollowerData
      }

    // RequestVote RPC
    case Event(
        RequestVote(term, lastLogIndex, lastLogTerm, rpcSender, _),
        d @ FSMData(currentTerm, opLog)) =>
      if (term < currentTerm) {
        clusterInterface ! RequestVoteResponse(currentTerm, voteGranted = false, thisNode, rpcSender)
        stay()
      } else {
        val newTerm = term

        val (shouldVoteFor, votedFor) = d match {
          case FollowerData(_, _, Some(vf)) if vf != rpcSender =>
            // Already voted for another peer
            (false, Some(vf))
          case _ =>
            // Check if sender's log is complete
            val shouldVoteFor =
              if (opLog.last.term < lastLogTerm) true
              else if (opLog.last.term == lastLogTerm) lastLogIndex >= opLog.length - 1
              else false
            (shouldVoteFor, None)
        }

        clusterInterface ! RequestVoteResponse(newTerm, voteGranted = shouldVoteFor, thisNode, rpcSender)

        val newFollowerData = FollowerData(newTerm, opLog, votedFor = votedFor)
        if (term > currentTerm && (stateName == Leader || stateName == Candidate))
          goto(Follower) using newFollowerData
        else
          stay() using newFollowerData
      }
  }

  when(Follower) {
    commonBehavior orElse {
      case Event(ElectionTick, FollowerData(currentTerm, opLog, _)) =>
        // Prevent false triggering
        val currentMillis = calendar.getTimeInMillis
        val falseTriggering = lastRPCFromLeader match {
          case Some(lastRPC) if lastRPC - currentMillis < electionTimeout.min => true
          case _ => false
        }
        if (!falseTriggering)
          goto(Candidate) using CandidateData(currentTerm.next, opLog, peers.toSet + thisNode, Set.empty)
        else
          stay()
    }
  }

  when(Candidate) {
    commonBehavior orElse {
      // RequestVote response from one of the peers
      case Event(RequestVoteResponse(responseTerm, voteGranted, rpcSender, _), d: CandidateData) =>
        // If discovers higher/equal term, become a Follower
        if (responseTerm >= d.currentTerm) {
          goto(Follower) using FollowerData(responseTerm, d.opLog, votedFor = None)
        }

        // If it's the last vote to get the majority, become the leader
        // Pay attention to double-senders
        else if (!d.votedForMe.contains(rpcSender) && voteGranted && d.votedForMe.size + 1 >= majority) {
          goto(Leader) using LeaderData(d.currentTerm, d.opLog)
        }

        else {
          log.info(s"Got vote from ${rpcSender.id}")
          sendToMonitoring(s"Got vote from ${rpcSender.id}")

          val newData = d.copy(
            requestVoteResultPending = d.requestVoteResultPending - rpcSender,
            votedForMe = if (voteGranted) d.votedForMe + rpcSender else d.votedForMe)
          setTimer(resendRequestVoteTimerName, RequestVoteRPCResendTick, rpcResendTimeout.timeout.millis)
          stay using newData
        }

      // Notification to resend RequestVote to peer that haven't answered yet
      case Event(RequestVoteRPCResendTick, CandidateData(currentTerm, opLog, pending, _)) =>
        pending.foreach { p =>
          clusterInterface !
            RequestVote(currentTerm, opLog.length - 1, opLog.last.term, thisNode, p)
        }
        setTimer(resendRequestVoteTimerName, RequestVoteRPCResendTick, rpcResendTimeout.timeout.millis)
        stay()

      // Notification of election timeout during election => haven't got the majority, restart the election
      case Event(ElectionTick, d: CandidateData) =>
        log.info("Didn't get the majority, restarting the election")
        sendToMonitoring("Didn't get the majority, restarting the election")
        goto(Candidate) using CandidateData(d.currentTerm.next, d.opLog, peers.toSet, Set(thisNode))
    }
  }

  onTransition {
    case _ -> Candidate =>
      log.info("Became a Candidate")
      sendToMonitoring("Became a Candidate")

      // When become a Candidate, request votes from peers
      (nextStateData: @unchecked) match {
        case CandidateData(term, opLog, pending, _) =>
          // Vote for self
          self ! RequestVoteResponse(term.prev, voteGranted = true, thisNode, thisNode)

          val pendingPeers = pending - thisNode
          pendingPeers.foreach { p =>
            clusterInterface !
              RequestVote(term, opLog.length - 1, opLog.last.term, thisNode, p)
          }
      }
      setTimer(electionTimerName, ElectionTick, electionTimeout.random)
      setTimer(resendRequestVoteTimerName, RequestVoteRPCResendTick, rpcResendTimeout.timeout.millis)

    case _ -> Leader =>
      log.info("Became the Leader")
      sendToMonitoring("Became the Leader")

      // When become the Leader, send heartbeat RPCs immediately
      (nextStateData: @unchecked) match {
        case LeaderData(term, _) =>
          sendHeartbeat(term)
          setTimer(heartbeatTimerName, HeartbeatTick, heartbeatTimeout)
      }

    case _ -> Follower =>
      log.info("Became a Follower")
      sendToMonitoring("Became a Follower")
  }

  when(Leader) {
    commonBehavior orElse {
      // Notification of heartbeat
      case Event(HeartbeatTick, LeaderData(currentTerm, _)) =>
        sendHeartbeat(currentTerm)
        setTimer(heartbeatTimerName, HeartbeatTick, heartbeatTimeout)
        stay()
    }
  }

  whenUnhandled {
    case Event(e, s) =>
      log.warning("Received unhandled request {} in state {}/{}", e, stateName, s)
      stay()
  }

  initialize()

  /**
   * Send heartbeat RPC to peers to ensure cluster integrity
   * @param term current term
   */
  private def sendHeartbeat(term: Term): Unit = {
    peers.foreach { p =>
      // TODO prevLogIndex etc.
      clusterInterface ! AppendEntries(term, 0, Term.start, List.empty, 0, thisNode, p)
    }
  }

  // TODO redesign for sending more information (like timestamp)
  /**
    * Send message to monitoring
   * @param msg message
   */
  private def sendToMonitoring(msg: String): Unit = {
    monitoringInterface ! MonitoringMessage(msg)
  }

  private def resetElectionTimer(): Unit = {
    cancelTimer(electionTimerName)
    setTimer(electionTimerName, ElectionTick, electionTimeout.random)
  }
}
