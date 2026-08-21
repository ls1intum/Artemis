package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single question-answer exchange from an Iris ask-user-mode session, including the reasoning behind the answer.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisQAExchangeDTO(long id, String question, String answer, String reasoning) {
}
