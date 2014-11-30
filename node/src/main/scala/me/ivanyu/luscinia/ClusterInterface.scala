package me.ivanyu.luscinia

import akka.actor.{ActorLogging, Props}
import me.ivanyu.luscinia.entities.{LogOperation, Node}
import spray.routing.HttpServiceActor

object ClusterInterface {
  sealed trait ClusterRPC {
    val sender: Node
    val receiver: Node
  }

  /**
   * AppendEntries RPC
   * @param term leader's term
   * @param prevLogIndex index of log entry immediately preceding new ones
   * @param prevLogTerm tern of prevLogIndex entry
   * @param entries log operations (empty for heartbeat; may send more than one for efficiency)
   * @param leaderCommit leader's commitIndex
   * @param sender sender-leader
   * @param receiver receiver-peer
   */
  case class AppendEntries(term: Int,
                           prevLogIndex: Int,
                           prevLogTerm: Int,
                           entries: List[LogOperation],
                           leaderCommit: Int,
                           sender: Node,
                           receiver: Node) extends ClusterRPC

  /**
   * AppendEntries RPC result
   * @param term receiver's (follower's) term
   * @param success true if the follower accepts request
   * @param sender sender-peer
   * @param receiver receiver-leader
   */
  case class AppendEntriesResult(term: Int,
                                 success: Boolean,
                                 sender: Node,
                                 receiver: Node) extends ClusterRPC

  /**
   * RequestVote RPC
   * @param term candidate's term
   * @param lastLogIndex index of candidate's last log entry
   * @param lastLogTerm term of candidate's last log entry
   * @param sender sender-candidate
   * @param receiver receiver-peer
   */
  case class RequestVote(term: Int,
                         lastLogIndex: Int,
                         lastLogTerm: Int,
                         sender: Node,
                         receiver: Node) extends ClusterRPC

  /**
   * RequestVote RPC response
   * @param term receiver's (follower's) term
   * @param voteGranted true means the candidate received voice
   * @param sender sender-peer
   * @param receiver receiver-candidate
   */
  case class RequestVoteResponse(term: Int,
                                 voteGranted: Boolean,
                                 sender: Node,
                                 receiver: Node) extends ClusterRPC

  def props(thisNode: Node, otherNodes: TraversableOnce[Node]): Props =
    Props(classOf[ClusterInterface], thisNode, otherNodes.toSet)
}


class ClusterInterface(val thisNode: Node, otherNodes: Set[Node])
  extends HttpServiceActor with ActorLogging {

  override def receive: Receive = {
    case _ =>
  }
}
