package de.tum.in.www1.artemis.service.dto;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * DTO for a StudentParticipation and its properties
 */
public class StudentParticipationDTO extends AuditingEntityDTO {
    // Participation

    private Long id;

    @Enumerated(EnumType.STRING)
    @JsonView(QuizView.Before.class)
    private InitializationState initializationState;

    @JsonView(QuizView.Before.class)
    private ZonedDateTime initializationDate;

    private ZonedDateTime individualDueDate;

    private Boolean testRun;

    private Exercise exercise;

    private Set<Result> results;

    private Set<Submission> submissions;

    private Integer submissionCount;

    // StudentParticipation
    private Integer presentationScore;

    private UserDTO student;

    private TeamDTO team;

    /**
     * Jackson constructor
     */
    public StudentParticipationDTO() {
    }

    public StudentParticipationDTO(StudentParticipation studentParticipation) {
        this(studentParticipation.getId(), studentParticipation.getInitializationState(), studentParticipation.getInitializationDate(), studentParticipation.getIndividualDueDate(),
                studentParticipation.isTestRun(), studentParticipation.getExercise(), studentParticipation.getResults(), studentParticipation.getSubmissions(),
                studentParticipation.getSubmissionCount(), studentParticipation.getPresentationScore(), studentParticipation.getStudent(), studentParticipation.getTeam());
    }

    public StudentParticipationDTO(Long id, InitializationState initializationState, ZonedDateTime initializationDate, ZonedDateTime individualDueDate, Boolean testRun,
            Exercise exercise, Set<Result> results, Set<Submission> submissions, Integer submissionCount, Integer presentationScore, Optional<User> student, Optional<Team> team) {
        this.id = id;
        this.initializationState = initializationState;
        this.initializationDate = initializationDate;
        this.individualDueDate = individualDueDate;
        this.testRun = testRun;
        this.exercise = exercise;
        this.results = results;
        this.submissions = submissions;
        this.submissionCount = submissionCount;
        this.presentationScore = presentationScore;
        student.ifPresent(user -> this.student = new UserDTO(user));
        team.ifPresent(value -> this.team = new TeamDTO(value));
    }

    public InitializationState getInitializationState() {
        return initializationState;
    }

    public void setInitializationState(InitializationState initializationState) {
        this.initializationState = initializationState;
    }

    public ZonedDateTime getInitializationDate() {
        return initializationDate;
    }

    public void setInitializationDate(ZonedDateTime initializationDate) {
        this.initializationDate = initializationDate;
    }

    public ZonedDateTime getIndividualDueDate() {
        return individualDueDate;
    }

    public void setIndividualDueDate(ZonedDateTime individualDueDate) {
        this.individualDueDate = individualDueDate;
    }

    public Boolean getTestRun() {
        return testRun;
    }

    public void setTestRun(Boolean testRun) {
        this.testRun = testRun;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Set<Result> getResults() {
        return results;
    }

    public void setResults(Set<Result> results) {
        this.results = results;
    }

    public Set<Submission> getSubmissions() {
        return submissions;
    }

    public void setSubmissions(Set<Submission> submissions) {
        this.submissions = submissions;
    }

    public Integer getSubmissionCount() {
        return submissionCount;
    }

    public void setSubmissionCount(Integer submissionCount) {
        this.submissionCount = submissionCount;
    }

    public Integer getPresentationScore() {
        return presentationScore;
    }

    public void setPresentationScore(Integer presentationScore) {
        this.presentationScore = presentationScore;
    }

    public UserDTO getStudent() {
        return student;
    }

    public void setStudent(UserDTO student) {
        this.student = student;
    }

    public TeamDTO getTeam() {
        return team;
    }

    public void setTeam(TeamDTO team) {
        this.team = team;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
