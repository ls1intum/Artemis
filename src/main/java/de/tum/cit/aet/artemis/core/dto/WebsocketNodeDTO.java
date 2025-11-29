package de.tum.cit.aet.artemis.core.dto;

/**
 * Information about a single websocket node in the Hazelcast cluster.
 *
 * @param memberId hazelcast member id
 * @param address  host:port address of the member
 * @param host     hostname of the member
 * @param port     port of the member
 * @param local    whether this node is the current node
 */
public record WebsocketNodeDTO(String memberId, String address, String host, int port, boolean local) {
}
