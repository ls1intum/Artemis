package de.tum.in.www1.artemis.web.rest.dto;

import java.util.Map;

public class CourseManagementStatisticsDTO {

    private Double averagePointsOfCourse;

    private Double maxPointsOfCourse;

    private Map<String, Double> exerciseNameToAveragePointsMap;

    private Map<String, Double> exerciseNameToMaxPointsMap;

    public Double getAveragePointsOfCourse() {
        return averagePointsOfCourse;
    }

    public void setAveragePointsOfCourse(Double averagePointsOfCourse) {
        this.averagePointsOfCourse = averagePointsOfCourse;
    }

    public Map<String, Double> getExerciseNameToAveragePointsMap() {
        return exerciseNameToAveragePointsMap;
    }

    public void setExerciseNameToAveragePointsMap(Map<String, Double> exerciseNameToAveragePointsMap) {
        this.exerciseNameToAveragePointsMap = exerciseNameToAveragePointsMap;
    }

    public Map<String, Double> getExerciseNameToMaxPointsMap() {
        return exerciseNameToMaxPointsMap;
    }

    public void setExerciseNameToMaxPointsMap(Map<String, Double> exerciseNameToMaxPointsMap) {
        this.exerciseNameToMaxPointsMap = exerciseNameToMaxPointsMap;
    }

    public Double getMaxPointsOfCourse() {
        return maxPointsOfCourse;
    }

    public void setMaxPointsOfCourse(Double maxPointsOfCourse) {
        this.maxPointsOfCourse = maxPointsOfCourse;
    }
}
