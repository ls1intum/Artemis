package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseInfoDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseDetailsDTO(Exercise exercise, PlagiarismCaseInfoDTO plagiarismCaseInfo) {
}
