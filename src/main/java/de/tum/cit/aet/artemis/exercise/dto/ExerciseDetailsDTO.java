package de.tum.cit.aet.artemis.exercise.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseInfoDTO;

/**
 * DTO wrapping the student-facing exercise details and optional plagiarism case information.
 *
 * @param exercise           the DTO-safe exercise details
 * @param plagiarismCaseInfo plagiarism case information for the current student, if available
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseDetailsDTO(ExerciseDetailsResponseDTO exercise, @Nullable PlagiarismCaseInfoDTO plagiarismCaseInfo) {

    /**
     * Creates the endpoint wrapper after the exercise entity has been authorized and filtered.
     *
     * @param exercise           the filtered exercise
     * @param plagiarismCaseInfo plagiarism case information for the current student, if available
     * @return the DTO-safe exercise details wrapper
     */
    public static ExerciseDetailsDTO of(de.tum.cit.aet.artemis.exercise.domain.Exercise exercise, @Nullable PlagiarismCaseInfoDTO plagiarismCaseInfo) {
        return new ExerciseDetailsDTO(ExerciseDetailsResponseDTO.of(exercise), plagiarismCaseInfo);
    }
}
