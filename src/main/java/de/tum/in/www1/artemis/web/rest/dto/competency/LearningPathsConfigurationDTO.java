package de.tum.in.www1.artemis.web.rest.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.LearningPathsConfiguration;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearningPathsConfigurationDTO(boolean includeAllGradedExercises) {

    public static LearningPathsConfigurationDTO of(LearningPathsConfiguration learningPathsConfiguration) {
        return new LearningPathsConfigurationDTO(learningPathsConfiguration.getIncludeAllGradedExercises());
    }
}
