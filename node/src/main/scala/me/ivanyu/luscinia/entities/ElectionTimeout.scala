package me.ivanyu.luscinia.entities

import scala.concurrent.duration._
import scala.util.Random

/**
 * Election timeout, random every time
 * @param min minimum timeout
 * @param max maximum timeout
 */
case class ElectionTimeout(min: Int, max: Int) {
  def random: FiniteDuration = {
    (Random.nextInt(max - min) + min).milliseconds
  }
}