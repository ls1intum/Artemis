package de.tum.cit.aet.artemis.exercise.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseInfoDTO;

/**
 * The response of the student exercise details route.
 *
 * @param exercise           the exercise with the participations of the requesting user
 * @param plagiarismCaseInfo the plagiarism case the requesting user was notified about, if any
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseDetailsDTO(ExerciseDetailsExerciseDTO exercise, @Nullable PlagiarismCaseInfoDTO plagiarismCaseInfo) {
}
