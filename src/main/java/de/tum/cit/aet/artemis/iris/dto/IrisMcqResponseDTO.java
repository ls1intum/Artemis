package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for persisting a student's response to an MCQ question.
 *
 * @param selectedIndex the index of the selected option
 * @param submitted     whether the response has been submitted
 * @param questionIndex the index of the question within an MCQ set, or null for standalone MCQs
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMcqResponseDTO(int selectedIndex, boolean submitted, @Nullable Integer questionIndex) {
}
