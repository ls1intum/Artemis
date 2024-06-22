package de.tum.in.www1.artemis.web.rest.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.service.iris.dto.IrisCombinedSettingsDTO;
import de.tum.in.www1.artemis.web.rest.dto.plagiarism.PlagiarismCaseInfoDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseDetailsDTO(Exercise exercise, IrisCombinedSettingsDTO irisSettings, PlagiarismCaseInfoDTO plagiarismCaseInfo, Set<ExerciseHint> availableExerciseHints,
        Set<ExerciseHint> activatedExerciseHints) {
}
