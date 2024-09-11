package de.tum.cit.aet.artemis.web.rest.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.CourseStatisticsAverageScore;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseManagementStatisticsDTO(Double averageScoreOfCourse, List<CourseStatisticsAverageScore> averageScoresOfExercises) {
}
