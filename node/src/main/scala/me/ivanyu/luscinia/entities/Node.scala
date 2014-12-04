package me.ivanyu.luscinia.entities

/**
 * Node representation
 * @param id identifier
 * @param clusterEndpoint cluster endpoint for connection with the peers
 * @param clientEndpoint client endpoint for client REST interface
 * @param monitoringEndpoint monitoring endpoint for monitoring web socket interface
 */
case class Node(id: String,
                clusterEndpoint: ClusterEndpoint,
                clientEndpoint: ClientEndpoint,
                monitoringEndpoint: MonitoringEndpoint)
