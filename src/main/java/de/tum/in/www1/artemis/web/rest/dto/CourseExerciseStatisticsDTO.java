package de.tum.in.www1.artemis.web.rest.dto;

public class CourseExerciseStatisticsDTO {

    private Long exerciseId;

    private String exerciseTitle;

    private String exerciseMode;

    private Double exerciseMaxPoints;

    private Double averageScoreInPercent;

    private Integer noOfParticipatingStudentsOrTeams;

    private Double participationRateInPercent;

    private Integer noOfStudentsInCourse;

    private Integer noOfTeamsInCourse;

    private Long noOfRatedAssessments;

    private Long noOfSubmissionsInTime;

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public String getExerciseTitle() {
        return exerciseTitle;
    }

    public void setExerciseTitle(String exerciseTitle) {
        this.exerciseTitle = exerciseTitle;
    }

    public String getExerciseMode() {
        return exerciseMode;
    }

    public void setExerciseMode(String exerciseMode) {
        this.exerciseMode = exerciseMode;
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
}
