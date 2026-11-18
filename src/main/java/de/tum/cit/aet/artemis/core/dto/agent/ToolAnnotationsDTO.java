package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Hints about a tool, for the agent to decide whether to ask the instructor first.
 *
 * @param readOnlyHint    whether the tool changes nothing
 * @param destructiveHint whether the tool replaces content that is already stored; no tool deletes anything
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ToolAnnotationsDTO(boolean readOnlyHint, boolean destructiveHint) {
}
