package de.tum.cit.aet.artemis.core.dto.agent;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The result of {@code tools/call}.
 *
 * @param content what the tool reports, as text
 * @param isError whether the tool failed
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CallToolResultDTO(List<ToolContentDTO> content, boolean isError) {
}
