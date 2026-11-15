package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What is wrong with one field of a rejected input.
 *
 * @param objectName the object the field belongs to
 * @param field      the field
 * @param message    what is wrong
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentFieldErrorDTO(String objectName, String field, String message) {
}
