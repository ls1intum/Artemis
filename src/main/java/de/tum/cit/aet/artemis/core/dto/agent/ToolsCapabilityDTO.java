package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The tool capability.
 *
 * @param listChanged whether the server announces changes of the tool list
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ToolsCapabilityDTO(boolean listChanged) {
}
