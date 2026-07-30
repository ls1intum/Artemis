package de.tum.cit.aet.artemis.atlas.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEnabledCourse;
import de.tum.cit.aet.artemis.course.domain.Course;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ScienceEnabledCourseDTO(Long courseId, String courseTitle, String courseShortName, boolean active, Instant createdDate, String createdBy, Instant lastModifiedDate,
        String lastModifiedBy) {

    public static ScienceEnabledCourseDTO of(ScienceEnabledCourse enabledCourse) {
        Course course = enabledCourse.getCourse();
        return new ScienceEnabledCourseDTO(course.getId(), course.getTitle(), course.getShortName(), enabledCourse.isActive(), enabledCourse.getCreatedDate(),
                enabledCourse.getCreatedBy(), enabledCourse.getLastModifiedDate(), enabledCourse.getLastModifiedBy());
    }
}
