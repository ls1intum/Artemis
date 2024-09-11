package de.tum.cit.aet.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseManagementOverviewExerciseStatisticsDTO {

    private Long exerciseId;

    private Double exerciseMaxPoints;

    private Double averageScoreInPercent;

    private Integer noOfParticipatingStudentsOrTeams;

    private Double participationRateInPercent;

    private Integer noOfStudentsInCourse;

    private Integer noOfTeamsInCourse;

    private Long noOfRatedAssessments;

    private Long noOfSubmissionsInTime;

    private Double noOfAssessmentsDoneInPercent;

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public Double getExerciseMaxPoints() {
        return exerciseMaxPoints;
    }

    public void setExerciseMaxPoints(Double exerciseMaxPoints) {
        this.exerciseMaxPoints = exerciseMaxPoints;
    }

    public Double getAverageScoreInPercent() {
        return averageScoreInPercent;
    }

    public void setAverageScoreInPercent(Double averageScoreInPercent) {
        this.averageScoreInPercent = averageScoreInPercent;
    }

    public Integer getNoOfParticipatingStudentsOrTeams() {
        return noOfParticipatingStudentsOrTeams;
    }

    public void setNoOfParticipatingStudentsOrTeams(Integer noOfParticipatingStudentsOrTeams) {
        this.noOfParticipatingStudentsOrTeams = noOfParticipatingStudentsOrTeams;
    }

    public Double getParticipationRateInPercent() {
        return participationRateInPercent;
    }

    public void setParticipationRateInPercent(Double participationRateInPercent) {
        this.participationRateInPercent = participationRateInPercent;
    }

    public Integer getNoOfStudentsInCourse() {
        return noOfStudentsInCourse;
    }

    public void setNoOfStudentsInCourse(Integer noOfStudentsInCourse) {
        this.noOfStudentsInCourse = noOfStudentsInCourse;
    }

    public Integer getNoOfTeamsInCourse() {
        return noOfTeamsInCourse;
    }

    public void setNoOfTeamsInCourse(Integer noOfTeamsInCourse) {
        this.noOfTeamsInCourse = noOfTeamsInCourse;
    }

    public Long getNoOfRatedAssessments() {
        return noOfRatedAssessments;
    }

    public void setNoOfRatedAssessments(Long noOfRatedAssessmentsInTime) {
        this.noOfRatedAssessments = noOfRatedAssessmentsInTime;
    }

    public Long getNoOfSubmissionsInTime() {
        return noOfSubmissionsInTime;
    }

    public void setNoOfSubmissionsInTime(Long noOfSubmissionsInTime) {
        this.noOfSubmissionsInTime = noOfSubmissionsInTime;
    }

    public Double getNoOfAssessmentsDoneInPercent() {
        return noOfAssessmentsDoneInPercent;
    }

    public void setNoOfAssessmentsDoneInPercent(Double noOfAssessmentsDoneInPercent) {
        this.noOfAssessmentsDoneInPercent = noOfAssessmentsDoneInPercent;
    }
}
