package me.ivanyu.luscinia

import akka.actor.{Actor, Props}
import akka.testkit.TestProbe

object TestTools {
  def probeProps(probe: TestProbe): Props = Props(new Actor {
    override def receive: Receive = {
      case msg => probe.ref forward msg
    }
  })

}
