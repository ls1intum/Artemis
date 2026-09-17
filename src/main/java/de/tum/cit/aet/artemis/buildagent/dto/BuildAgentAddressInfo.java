package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Set;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The network addresses a build agent is observed to connect to the cluster from.
 * <p>
 * Written by the core nodes rather than by the agent, and derived from the remote address of the agent's own
 * cluster connection. An agent cannot influence its entry, which is what makes this usable for authorizing the
 * agent's git requests: {@code BuildAgentDTO.memberAddress} is the agent's view of its local socket, so it is
 * pre-NAT and forgeable, while these addresses are what the middleware actually accepted the connection from.
 * <p>
 * An entry exists only while the agent holds a live cluster connection. That is what lets the per-build-job
 * clone token do without an expiry: a crashed agent that left a job behind in the processing list no longer has
 * a registered address, so a token recovered from that job fails the origin check.
 * <p>
 * NOTE: this data structure is shared between core and build agent nodes. Changing it requires that the shared
 * data structures in Hazelcast (or Redis) are migrated or cleared. Changes should be communicated in release
 * notes as potentially breaking changes.
 *
 * @param agentName       the build agent short name, which is also the key of this entry, the key of the build
 *                            agent information map and the name the middleware knows the client connection by
 * @param addresses       the host addresses the agent is currently observed to connect from, without ports.
 *                            More than one is normal while an agent reconnects, and several agents behind one
 *                            NAT gateway legitimately share an address
 * @param observedAt      when a core node last refreshed this entry
 * @param withinAllowlist whether every observed address lies inside the configured build agent network ranges.
 *                            Always {@code true} when no ranges are configured, since an empty allowlist means
 *                            no restriction
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildAgentAddressInfo(@NonNull String agentName, @NonNull Set<String> addresses, @NonNull ZonedDateTime observedAt, boolean withinAllowlist) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
