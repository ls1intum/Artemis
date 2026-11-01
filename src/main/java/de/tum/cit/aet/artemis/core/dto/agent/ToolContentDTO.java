package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One piece of what a tool reports.
 *
 * @param type always {@code text}
 * @param text the text
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ToolContentDTO(String type, String text) {
}
