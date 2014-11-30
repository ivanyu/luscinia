package me.ivanyu.luscinia

import akka.actor.{ActorLogging, Props}
import me.ivanyu.luscinia.entities.Node
import spray.routing.HttpServiceActor

object ClusterInterface {
  sealed trait ClusterRPC

/*
  /**
   * AppendEntries RPC
   * @param term leader's term
   * @param prevLogIndex index of log entry immediately preceding new ones
   * @param prevLogTerm tern of prevLogIndex entry
   * @param entries log operations (empty for heartbeat; may send more than one for efficiency)
   * @param leaderCommit leader's commitIndex
   */
  case class AppendEntries(term: Int,
                           prevLogIndex: Int,
                           prevLogTerm: Int,
                           entries: List[LogOperation],
                           leaderCommit: Int) extends ClusterRPC

  /**
   * AppendEntries RPC result
   * @param term receiver's (follower's) term
   * @param success true if the follower accepts request
   */
  case class AppendEntriesResult(term: Int,
                                 success: Boolean) extends ClusterRPC
*/

  /**
   * RequestVote RPC
   * @param term candidate's term
   * @param lastLogIndex index of candidate's last log entry
   * @param lastLogTerm term of candidate's last log entry
   * @param candidate candidate
   * @param receiver receiver of the RPC
   */
  case class RequestVote(term: Int,
                         lastLogIndex: Int,
                         lastLogTerm: Int,
                         candidate: Node,
                         receiver: Node) extends ClusterRPC

  /**
   * RequestVote RPC response
   * @param term receiver's (follower's) term
   * @param voteGranted true means the candidate received voice
   * @param sender sender of the response
   */
  case class RequestVoteResponse(term: Int,
                                 voteGranted: Boolean,
                                 sender: Node) extends ClusterRPC

  def props(thisNode: Node, otherNodes: TraversableOnce[Node]): Props =
    Props(classOf[ClusterInterface], thisNode, otherNodes.toSet)
}


class ClusterInterface(val thisNode: Node, otherNodes: Set[Node])
  extends HttpServiceActor with ActorLogging {

  override def receive: Receive = {
    case _ =>
  }
}
