package de.tum.cit.aet.artemis.atlas.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;

/**
 * A DTO containing the information of the linkedCourseCompetency field of a
 * {@link CourseCompetency CourseCompetency}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LinkedCourseCompetencyDTO(long id, long courseId, @Nullable String courseTitle, @Nullable String semester) {

    /**
     * Maps a linked course competency to a DTO.
     *
     * @param linkedCompetency the linked competency to map
     * @return the DTO or null if the competency or course is null
     */
    public static @Nullable LinkedCourseCompetencyDTO of(@Nullable CourseCompetency linkedCompetency) {
        if (linkedCompetency == null || linkedCompetency.getCourse() == null) {
            return null;
        }
        var course = linkedCompetency.getCourse();
        return new LinkedCourseCompetencyDTO(linkedCompetency.getId(), course.getId(), course.getTitle(), course.getSemester());
    }
}
