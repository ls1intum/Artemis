package de.tum.cit.aet.artemis.core.service.distributed;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What a node publishes about itself into the {@link NodeRegistryService}.
 *
 * <p>
 * Only core nodes register, so every entry describes a node that serves websocket traffic. That is deliberately narrower
 * than Hazelcast's member list, which also contains build agents as lite members and forced callers to filter them out.
 *
 * @param nodeId     stable identifier of the node within the cluster
 * @param address    the address other nodes reach it on, in {@code host:port} form where known
 * @param host       host part of the address
 * @param port       port part of the address, or 0 if the provider does not expose one
 * @param instanceId human-readable instance name, taken from the Eureka instance id where configured
 */
// NOTE: shared between nodes, so changing it requires clearing the registry map or accepting one heartbeat interval of
// unreadable entries. Entries expire on their own, so no migration is needed.
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ClusterNodeInfo(String nodeId, String address, String host, int port, String instanceId) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
