package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

/**
 * DTO for a learning navigation object.
 *
 * @param id        the id of the learning object
 * @param completed whether the learning object is completed
 * @param name      the name of the learning object
 * @param type      the type of the learning object
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearningPathNavigationObjectDTO(long id, boolean completed, String name, long competencyId, LearningObjectType type, boolean repeatedTest, boolean unreleased) {

    /**
     * Create a navigation object DTO from a learning object.
     *
     * @param learningObject the learning object
     * @param completed      whether the learning object is completed by the user
     * @return the navigation object DTO
     */
    public static LearningPathNavigationObjectDTO of(LearningObject learningObject, boolean repeatedTest, boolean completed, long competencyId) {
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

        return new LearningPathNavigationObjectDTO(id, completed, name, competencyId, type, repeatedTest, unreleased);
    }

    public enum LearningObjectType {
        LECTURE, EXERCISE
    }

    /**
     * Check if the learning object represented by this dto is equal to the given learning object in the competency.
     *
     * @param learningObject the learning object to compare to
     * @param competency     the competency to compare to
     * @return whether the learning object is equal
     */
    public boolean equalsLearningObject(LearningObject learningObject, CourseCompetency competency) {
        if (learningObject == null || id != learningObject.getId() || competencyId != competency.getId()) {
            return false;
        }

        return switch (learningObject) {
            case LectureUnit ignored -> type == LearningObjectType.LECTURE;
            case Exercise ignored -> type == LearningObjectType.EXERCISE;
            default -> false;
        };
    }
}
