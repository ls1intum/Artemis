package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Set;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A build agent periodically asks the core's git endpoint which address it observed and publishes the result under
 * its short name. The provider expires the report when the agent stops refreshing it. Core nodes use the report to
 * check the origin of git requests and to display the agent's network status.
 * <p>
 * Core and build agents share this record. Changes to its serialized form must account for existing Redis entries.
 *
 * @param agentName       the build agent short name and key of the report
 * @param addresses       the host addresses observed at the git endpoint, without ports
 * @param observedAt      when the agent measured the address
 * @param withinAllowlist whether all addresses fall within the configured build agent network ranges, computed by
 *                            the core for display. An empty allowlist permits every address.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildAgentAddressInfo(@NonNull String agentName, @NonNull Set<String> addresses, @NonNull ZonedDateTime observedAt, boolean withinAllowlist) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
