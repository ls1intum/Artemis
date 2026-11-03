package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What the server offers: tools, and no change of the list while a session runs, because there is no session.
 *
 * @param tools the tool capability
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CapabilitiesDTO(ToolsCapabilityDTO tools) {
}
