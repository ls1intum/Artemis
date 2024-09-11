package de.tum.cit.aet.artemis.web.rest.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.LearningObject;
import de.tum.cit.aet.artemis.domain.lecture.LectureUnit;

/**
 * DTO for a learning navigation object.
 *
 * @param id        the id of the learning object
 * @param completed whether the learning object is completed
 * @param name      the name of the learning object
 * @param type      the type of the learning object
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearningPathNavigationObjectDTO(long id, boolean completed, String name, long competencyId, LearningObjectType type, boolean unreleased) {

    /**
     * Create a navigation object DTO from a learning object.
     *
     * @param learningObject the learning object
     * @param completed      whether the learning object is completed by the user
     * @return the navigation object DTO
     */
    public static LearningPathNavigationObjectDTO of(LearningObject learningObject, boolean completed, long competencyId) {
        long id = learningObject.getId();
        String name;
        LearningObjectType type;
        boolean unreleased = !learningObject.isVisibleToStudents();

        switch (learningObject) {
            case LectureUnit lectureUnit -> {
                name = lectureUnit.getName();
                type = LearningObjectType.LECTURE;
            }
            case Exercise exercise -> {
                name = exercise.getTitle();
                type = LearningObjectType.EXERCISE;
            }
            default -> throw new IllegalArgumentException("Learning object must be either LectureUnit or Exercise");
        }

        name = unreleased ? "" : name;

        return new LearningPathNavigationObjectDTO(id, completed, name, competencyId, type, unreleased);
    }

    public enum LearningObjectType {
        LECTURE, EXERCISE
    }
}
