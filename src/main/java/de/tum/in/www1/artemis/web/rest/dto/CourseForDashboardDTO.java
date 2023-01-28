package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Result;

/**
 * Returned by the for-dashboard resources.
 * Contains a course (e.g. shown in the course-card.component) and different types of scores.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForDashboardDTO(Course course, Map<String, CourseScoresDTO> scoresPerExerciseType, List<Result> participationResults) {
}
