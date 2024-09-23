package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyProgressForLearningPathDTO(long competencyId, double masteryThreshold, double progress, double confidence) {
}
