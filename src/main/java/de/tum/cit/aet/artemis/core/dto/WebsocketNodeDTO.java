package de.tum.cit.aet.artemis.core.dto;

/**
 * Information about a single websocket node in the Hazelcast cluster.
 *
 * @param memberId        hazelcast member id
 * @param address         host:port address of the member
 * @param host            hostname of the member
 * @param port            port of the member
 * @param local           whether this node is the current node
 * @param liteMember      whether this node is a lite (build agent) member without broker connectivity
 * @param instanceId      optional discovery instance id (e.g. eureka.instance.instanceId)
 * @param brokerConnected whether this node currently reports the websocket broker as available
 */
public record WebsocketNodeDTO(String memberId, String address, String host, int port, boolean local, boolean liteMember, String instanceId, boolean brokerConnected) {
}
