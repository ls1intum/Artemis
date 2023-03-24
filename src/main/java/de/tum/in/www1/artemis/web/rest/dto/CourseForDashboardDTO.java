package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;

/**
 * Returned by the for-dashboard resources.
 * Contains a course (e.g. shown in the course-card.component) and different types of scores.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForDashboardDTO(Course course, CourseScoresDTO totalScores, Map<ExerciseType, CourseScoresDTO> scoresPerExerciseType, List<Result> participationResults) {
}
