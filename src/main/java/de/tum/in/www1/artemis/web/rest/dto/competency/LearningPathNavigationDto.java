package de.tum.in.www1.artemis.web.rest.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.LearningObject;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LearningPathNavigationDto(LearningPathNavigationObjectDto predecessorLearningObject, LearningPathNavigationObjectDto currentLearningObject,
        LearningPathNavigationObjectDto successorLearningObject, int progress) {

    public record LearningPathNavigationObjectDto(Long id, boolean completed, String name, LearningObjectType type) {

        public static LearningPathNavigationObjectDto of(LearningObject learningObject, User user) {
            if (learningObject == null) {
                return null;
            }
            return switch (learningObject) {
                case LectureUnit lectureUnit ->
                    new LearningPathNavigationObjectDto(lectureUnit.getId(), learningObject.isCompletedFor(user), lectureUnit.getName(), LearningObjectType.LECTURE);
                case Exercise exercise ->
                    new LearningPathNavigationObjectDto(learningObject.getId(), learningObject.isCompletedFor(user), exercise.getTitle(), LearningObjectType.EXERCISE);
                default -> throw new IllegalArgumentException("Learning object must be either LectureUnit or Exercise");
            };
        }

        public enum LearningObjectType {
            LECTURE, EXERCISE
        }
    }
}
