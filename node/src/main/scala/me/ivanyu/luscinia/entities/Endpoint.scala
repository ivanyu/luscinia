package me.ivanyu.luscinia.entities

sealed trait Endpoint {
  def host: String
  def port: Int
}

case class ClusterEndpoint(host: String, port: Int) extends Endpoint
case class ClientEndpoint(host: String, port: Int) extends Endpoint
