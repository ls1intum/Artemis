package de.tum.cit.aet.artemis.atlas.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseInfoDTO(Long id, String title, String semester) {

    /**
     * Maps a course to a course info DTO.
     *
     * @param course the course to map
     * @return the DTO or null if the course is null
     */
    @Nullable
    public static CourseInfoDTO of(@Nullable Course course) {
        if (course == null) {
            return null;
        }
        return new CourseInfoDTO(course.getId(), course.getTitle(), course.getSemester());
    }
}
