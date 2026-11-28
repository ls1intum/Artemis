package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Hints about a tool, for the agent to decide whether to ask the instructor first.
 *
 * @param readOnlyHint    whether the tool changes nothing
 * @param destructiveHint whether the tool replaces content that is already stored; no tool deletes anything
 * @param openWorldHint   whether the tool reaches outside Artemis; none does, but the protocol assumes it does unless told otherwise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ToolAnnotationsDTO(boolean readOnlyHint, boolean destructiveHint, boolean openWorldHint) {
}
