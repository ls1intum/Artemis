package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearningPathDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean startedByStudent,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int progress) {

    public static LearningPathDTO of(LearningPath learningPath) {
        return new LearningPathDTO(learningPath.getId(), learningPath.isStartedByStudent(), learningPath.getProgress());
    }
}
