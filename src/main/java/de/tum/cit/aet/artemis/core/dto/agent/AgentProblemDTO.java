package de.tum.cit.aet.artemis.core.dto.agent;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The problem document a failed call of the REST API answers with, as far as an agent needs it to correct itself. Artemis puts the
 * sentence for people in the title and an error key in the message.
 *
 * @param detail      the explanation of the problem, if there is one
 * @param title       the sentence for people
 * @param message     the error key
 * @param fieldErrors what is wrong with which field, for a rejected input
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentProblemDTO(String detail, String title, String message, List<AgentFieldErrorDTO> fieldErrors) {
}
