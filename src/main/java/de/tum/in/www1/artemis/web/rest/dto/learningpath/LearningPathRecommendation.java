package de.tum.in.www1.artemis.web.rest.dto.learningpath;

public record LearningPathRecommendation(long learningObjectId, long lectureId, RecommendationType type) {

    public enum RecommendationType {
        EMPTY, LECTURE_UNIT, EXERCISE
    }
}
