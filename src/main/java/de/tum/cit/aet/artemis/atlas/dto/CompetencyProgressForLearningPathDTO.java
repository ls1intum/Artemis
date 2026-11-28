package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyProgressForLearningPathDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long competencyId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double masteryThreshold, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double progress,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double confidence) {
}
