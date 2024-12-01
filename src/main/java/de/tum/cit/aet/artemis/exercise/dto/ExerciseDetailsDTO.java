package de.tum.cit.aet.artemis.exercise.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedSettingsDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseInfoDTO;
import de.tum.cit.aet.artemis.programming.domain.hestia.ExerciseHint;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseDetailsDTO(Exercise exercise, IrisCombinedSettingsDTO irisSettings, PlagiarismCaseInfoDTO plagiarismCaseInfo, Set<ExerciseHint> availableExerciseHints,
        Set<ExerciseHint> activatedExerciseHints) {
}
