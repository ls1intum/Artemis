package de.tum.cit.aet.artemis.core.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseManagementStatisticsDTO(Double averageScoreOfCourse, List<CourseStatisticsAverageScore> averageScoresOfExercises) {
}
