package de.tum.cit.aet.artemis.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * The course data the course overview container itself needs: the course record plus its notification count.
 * <p>
 * No exercises, lectures, exams, participations or scores are included — each tab loads what it needs, and which tabs to
 * offer comes from {@link CourseAvailableTabsDTO}.
 *
 * @param course                  the course, without its content collections
 * @param courseNotificationCount the number of unread notifications for the course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForOverviewDTO(Course course, long courseNotificationCount) {
}
