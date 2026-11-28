package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Information about a single websocket node in the Artemis cluster.
 *
 * @param memberId        Artemis node identifier
 * @param address         host:port address of the member
 * @param host            hostname of the member
 * @param port            port of the member
 * @param local           whether this node is the current node
 * @param instanceId      configured Artemis instance identifier
 * @param brokerConnected whether this node currently reports the websocket broker as available
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WebsocketNodeDTO(String memberId, String address, String host, int port, boolean local, String instanceId, boolean brokerConnected) {
}
