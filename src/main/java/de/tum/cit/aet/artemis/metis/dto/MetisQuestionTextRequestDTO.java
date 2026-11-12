package de.tum.cit.aet.artemis.metis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The new text of a main question, written by an editor during the review of the generated questions.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisQuestionTextRequestDTO(@NotBlank @Size(min = 1, max = 2000) String questionText) {
}
