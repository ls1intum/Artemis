package de.tum.in.www1.artemis.web.rest.dto;

import org.springframework.cloud.cloudfoundry.com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Contains the scores that enable the client to show statistics for a calling student.
 * This includes the percentage of points reached for a course in the course-card.component and the statistics shown in the course-statistics.component.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseScoresForStudentStatisticsDTO(double maxPoints, double reachablePoints,
                                                  StudentScoreForStudentStatistics studentScoreForStudentStatistics) {

    public record StudentScoreForStudentStatistics(double absoluteScore, double relativeScore,
                                                   double currentRelativeScore, int presentationScore) {
    }

    public CourseScoresForStudentStatisticsDTO() {
        this(0.0, 0.0, new StudentScoreForStudentStatistics(0.0, 0.0, 0.0, 0));
    }
}
