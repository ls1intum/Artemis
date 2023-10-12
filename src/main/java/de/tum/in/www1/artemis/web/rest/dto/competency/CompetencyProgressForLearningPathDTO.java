package de.tum.in.www1.artemis.web.rest.dto.competency;

public record CompetencyProgressForLearningPathDTO(long competencyId, double masteryThreshold, double progress, double confidence) {
}
