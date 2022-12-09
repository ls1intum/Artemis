package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Returned by the for-dashboard resources.
 * Contains a course (e.g. shown in the course-card.component) and different types of scores.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseForDashboardDTO(Course course,
                                    Map<String, CourseScoresForStudentStatisticsDTO> scoresPerExerciseType,
                                    List<Result> participationResults) {
}
