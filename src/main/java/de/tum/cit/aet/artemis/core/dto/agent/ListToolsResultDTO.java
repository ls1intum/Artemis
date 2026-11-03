package de.tum.cit.aet.artemis.core.dto.agent;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The result of {@code tools/list}.
 *
 * @param tools the tools the token may use
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ListToolsResultDTO(List<ToolDescriptionDTO> tools) {
}
