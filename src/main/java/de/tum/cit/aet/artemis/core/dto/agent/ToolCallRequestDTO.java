package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A {@code tools/call} request, read a second time once the tool is known, so that its arguments are read as the type that tool
 * declares.
 *
 * @param params the name of the tool and its arguments
 * @param <A>    the arguments of the tool
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolCallRequestDTO<A>(ToolCallParamsDTO<A> params) {
}
