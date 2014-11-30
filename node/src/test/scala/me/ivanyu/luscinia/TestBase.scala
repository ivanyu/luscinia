package me.ivanyu.luscinia

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import me.ivanyu.luscinia.entities.{RPCResendTimeout, ElectionTimeout}
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}

abstract class TestBase extends TestKit(ActorSystem("Test"))
    with FunSuiteLike
//    with FlatSpecLike
//    with WordSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ImplicitSender {

  protected val electionTimeout = ElectionTimeout(150, 300)
  protected val rpcResendTimeout = RPCResendTimeout(60)

  protected val timingEpsilon = 30

  override protected def afterAll(): Unit = system.shutdown()
}
