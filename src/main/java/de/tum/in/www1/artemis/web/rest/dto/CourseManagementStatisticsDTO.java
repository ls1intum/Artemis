package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

import de.tum.in.www1.artemis.domain.statistics.CourseStatisticsAverageScore;

public class CourseManagementStatisticsDTO {

    private Double averageScoreOfCourse;

    private List<CourseStatisticsAverageScore> averageScoresOfExercises;

    public Double getAverageScoreOfCourse() {
        return averageScoreOfCourse;
    }

    public void setAverageScoreOfCourse(Double averageScoreOfCourse) {
        this.averageScoreOfCourse = averageScoreOfCourse;
    }

    public List<CourseStatisticsAverageScore> getAverageScoresOfExercises() {
        return averageScoresOfExercises;
    }

    public void setAverageScoresOfExercises(List<CourseStatisticsAverageScore> averageScoresOfExercises) {
        this.averageScoresOfExercises = averageScoresOfExercises;
    }
}
