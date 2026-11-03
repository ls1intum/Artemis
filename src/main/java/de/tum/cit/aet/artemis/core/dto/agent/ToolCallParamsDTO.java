package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The parameters of a tool call.
 *
 * @param name      the name of the tool
 * @param arguments what the agent sent for it
 * @param <A>       the arguments of the tool
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolCallParamsDTO<A>(String name, A arguments) {
}
