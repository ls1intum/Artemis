package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;
import java.util.Set;

public class CourseManagementOverviewExerciseStatisticsDTO {
    private Long exerciseId;

    private String exerciseTitle;

    private String exerciseType;

    private ZonedDateTime releaseDate;

    private ZonedDateTime dueDate;

    private ZonedDateTime assessmentDueDate;

    private Set<String> categories;

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

    public String getExerciseTitle() {
        return exerciseTitle;
    }

    public void setExerciseTitle(String exerciseTitle) {
        this.exerciseTitle = exerciseTitle;
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

    public Set<String> getCategories() {
        return categories;
    }

    public void setCategories(Set<String> categories) {
        this.categories = categories;
    }

    public String getExerciseType() {
        return exerciseType;
    }

    public void setExerciseType(String exerciseType) {
        this.exerciseType = exerciseType;
    }

    public ZonedDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(ZonedDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    public ZonedDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(ZonedDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public ZonedDateTime getAssessmentDueDate() {
        return assessmentDueDate;
    }

    public void setAssessmentDueDate(ZonedDateTime assessmentDueDate) {
        this.assessmentDueDate = assessmentDueDate;
    }

    public Double getNoOfAssessmentsDoneInPercent() {
        return noOfAssessmentsDoneInPercent;
    }

    public void setNoOfAssessmentsDoneInPercent(Double noOfAssessmentsDoneInPercent) {
        this.noOfAssessmentsDoneInPercent = noOfAssessmentsDoneInPercent;
    }
}
