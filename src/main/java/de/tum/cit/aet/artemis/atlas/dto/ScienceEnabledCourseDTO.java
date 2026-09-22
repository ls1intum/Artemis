package de.tum.cit.aet.artemis.atlas.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEnabledCourse;
import de.tum.cit.aet.artemis.course.domain.Course;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ScienceEnabledCourseDTO(Long courseId, String courseTitle, String courseShortName, boolean active, Instant createdDate, String createdBy, Instant lastModifiedDate,
        String lastModifiedBy) {

    public static ScienceEnabledCourseDTO of(ScienceEnabledCourse enabledCourse) {
        return of(enabledCourse, enabledCourse.getCourse());
    }

    /**
     * Builds the DTO from an entry whose {@code course} may not be usable, taking the course from the caller instead.
     * <p>
     * {@code save} on a detached entry is a {@code merge}, and the managed copy it returns holds an uninitialized proxy
     * for {@code course}: reading its title after the session closed threw, and the write it reported had in fact
     * succeeded. The caller already holds the real course, so it passes it rather than reaching through the entry.
     *
     * @param enabledCourse the stored enablement entry
     * @param course        the course the entry refers to
     * @return the DTO
     */
    public static ScienceEnabledCourseDTO of(ScienceEnabledCourse enabledCourse, Course course) {
        return new ScienceEnabledCourseDTO(course.getId(), course.getTitle(), course.getShortName(), enabledCourse.isActive(), enabledCourse.getCreatedDate(),
                enabledCourse.getCreatedBy(), enabledCourse.getLastModifiedDate(), enabledCourse.getLastModifiedBy());
    }
}
