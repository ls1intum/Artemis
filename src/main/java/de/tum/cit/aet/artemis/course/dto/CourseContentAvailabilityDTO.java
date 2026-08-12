package de.tum.cit.aet.artemis.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Whether a course has content of each kind, answered in a single query.
 * <p>
 * These are the raw database answers only. Whether the corresponding tab is actually offered also depends on the feature
 * module being enabled, which {@code CourseAvailableTabsService} applies on top — see {@link CourseAvailableTabsDTO}.
 * A disabled module must hide its tab even when rows exist.
 *
 * @param lectures        whether the course has at least one lecture
 * @param competencies    whether the course has at least one competency or prerequisite
 * @param tutorialGroups  whether the course has at least one tutorial group
 * @param acceptedFaqs    whether the course has at least one accepted FAQ
 * @param practiceQuizzes whether the course has quiz questions whose exercise is past its due date
 * @param visibleExams    whether at least one exam of the course is already visible to the user
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseContentAvailabilityDTO(boolean lectures, boolean competencies, boolean tutorialGroups, boolean acceptedFaqs, boolean practiceQuizzes, boolean visibleExams) {
}
