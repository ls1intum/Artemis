package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A tool as an agent sees it.
 *
 * @param name        the name the agent calls it by
 * @param description what it does and when to use it
 * @param inputSchema the schema of its arguments
 * @param annotations hints about what it does
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ToolDescriptionDTO(String name, String description, JsonSchemaDTO inputSchema, ToolAnnotationsDTO annotations) {
}
