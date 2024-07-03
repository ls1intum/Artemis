package de.tum.in.www1.artemis.web.rest.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.LearningObject;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;

/**
 * DTO for a learning navigation object.
 *
 * @param id        the id of the learning object
 * @param completed whether the learning object is completed
 * @param name      the name of the learning object
 * @param type      the type of the learning object
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearningPathNavigationObjectDTO(long id, boolean completed, String name, long competencyId, LearningObjectType type) {

    /**
     * Create a navigation object DTO from a learning object.
     *
     * @param learningObject the learning object
     * @param completed      whether the learning object is completed by the user
     * @return the navigation object DTO
     */
    public static LearningPathNavigationObjectDTO of(LearningObject learningObject, boolean completed, long competencyId) {
        return switch (learningObject) {
            case LectureUnit lectureUnit -> new LearningPathNavigationObjectDTO(lectureUnit.getId(), completed, lectureUnit.getName(), competencyId, LearningObjectType.LECTURE);
            case Exercise exercise -> new LearningPathNavigationObjectDTO(learningObject.getId(), completed, exercise.getTitle(), competencyId, LearningObjectType.EXERCISE);
            default -> throw new IllegalArgumentException("Learning object must be either LectureUnit or Exercise");
        };
    }

    public enum LearningObjectType {
        LECTURE, EXERCISE
    }
}
