package de.tum.cit.aet.artemis.course.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;

/**
 * A lightweight course row for management overview and course-selection screens.
 *
 * @param id                                    the course identifier
 * @param title                                 the course title
 * @param shortName                             the course short name
 * @param description                           the optional description
 * @param startDate                             the optional start date
 * @param endDate                               the optional end date
 * @param semester                              the optional semester
 * @param testCourse                            whether this is a test course
 * @param color                                 the optional display color
 * @param courseIcon                            the optional icon
 * @param timeZone                              the optional time zone
 * @param courseInformationSharingConfiguration the optional communication configuration
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseManagementOverviewDTO(long id, String title, String shortName, @Nullable String description, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime endDate,
        @Nullable String semester, boolean testCourse, @Nullable String color, @Nullable String courseIcon, @Nullable String timeZone,
        @Nullable CourseInformationSharingConfiguration courseInformationSharingConfiguration) {

    /**
     * Maps a course without traversing its associations.
     *
     * @param course the course to map
     * @return the management overview row
     */
    public static CourseManagementOverviewDTO of(Course course) {
        return new CourseManagementOverviewDTO(course.getId(), course.getTitle(), course.getShortName(), course.getDescription(), course.getStartDate(), course.getEndDate(),
                course.getSemester(), course.isTestCourse(), course.getColor(), course.getCourseIcon(), course.getTimeZone(), course.getCourseInformationSharingConfiguration());
    }
}
