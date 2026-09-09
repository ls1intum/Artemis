package de.tum.cit.aet.artemis.course.dto;

import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * The limited course information shown before a user enrolls.
 *
 * @param id                            the course identifier
 * @param title                         the course title
 * @param description                   the optional course description
 * @param semester                      the optional semester label
 * @param enrollmentConfirmationMessage the optional confirmation message
 * @param prerequisites                 the prerequisites that are initialized for the enrollment response
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForEnrollmentDTO(long id, String title, @Nullable String description, @Nullable String semester, @Nullable String enrollmentConfirmationMessage,
        Set<CoursePrerequisiteDTO> prerequisites) {

    /**
     * Maps a course and its already initialized prerequisites for enrollment.
     *
     * @param course the course to map
     * @return the enrollment response
     */
    public static CourseForEnrollmentDTO of(Course course) {
        Set<CoursePrerequisiteDTO> prerequisites = Hibernate.isInitialized(course.getPrerequisites())
                ? course.getPrerequisites().stream().map(CoursePrerequisiteDTO::of).collect(Collectors.toUnmodifiableSet())
                : Set.of();
        return new CourseForEnrollmentDTO(course.getId(), course.getTitle(), course.getDescription(), course.getSemester(), course.getEnrollmentConfirmationMessage(),
                prerequisites);
    }
}
