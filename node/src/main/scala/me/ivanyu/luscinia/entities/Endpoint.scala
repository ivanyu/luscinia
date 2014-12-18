package me.ivanyu.luscinia.entities

/**
 * Network endpoint
 */
sealed trait Endpoint {
  // Address
  val address: String
  // Port
  val port: Int
}

/**
 * Network endpoint to connect to the peers
 * @param address address
 * @param port port
 */
case class ClusterEndpoint(address: String, port: Int) extends Endpoint

/**
 * Network endpoint for client connection
 * @param address address
 * @param port port
 */
case class ClientEndpoint(address: String, port: Int) extends Endpoint

/**
 * Network endpoint for monitoring
 * @param address address
 * @param port port
 */
case class MonitoringEndpoint(address: String, port: Int) extends Endpoint
