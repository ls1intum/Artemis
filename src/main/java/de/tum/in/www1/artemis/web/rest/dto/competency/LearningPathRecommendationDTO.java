package de.tum.in.www1.artemis.web.rest.dto.competency;

public record LearningPathRecommendationDTO(long learningObjectId, long lectureId, RecommendationType type) {

    public enum RecommendationType {
        EMPTY, LECTURE_UNIT, EXERCISE
    }
}
