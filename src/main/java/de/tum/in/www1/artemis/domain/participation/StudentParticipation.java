package de.tum.in.www1.artemis.domain.participation;

import java.util.Optional;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.view.QuizView;

@Entity
@DiscriminatorValue(value = "SP")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StudentParticipation extends Participation {

    @Column(name = "presentation_score")
    private Integer presentationScore;

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private User student;

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private Team team;

    public Integer getPresentationScore() {
        return presentationScore;
    }

    public void setPresentationScore(Integer presentationScore) {
        this.presentationScore = presentationScore;
    }

    public Optional<User> getStudent() {
        return Optional.ofNullable(student);
    }

    public Optional<Team> getTeam() {
        return Optional.ofNullable(team);
    }

    @JsonIgnore
    public Set<User> getStudents() {
        return getStudent().map(Set::of).orElseGet(() -> team != null ? team.getStudents() : Set.of());
    }

    @JsonIgnore
    public Participant getParticipant() {
        return Optional.ofNullable((Participant) student).orElse(team);
    }

    /**
     * allows to set the participant independent whether it is a team or user
     * @param participant either a team or user
     */
    public void setParticipant(Participant participant) {
        if (participant instanceof User) {
            this.student = (User) participant;
        }
        else if (participant instanceof Team) {
            this.team = (Team) participant;
        }
        else if (participant == null) {
            this.student = null;
            if (this.team != null) {
                this.team.setStudents(null);
            }
        }
        else {
            throw new Error("Unknown participant type");
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getParticipantIdentifier() {
        return Optional.ofNullable(getParticipant()).map(Participant::getParticipantIdentifier).orElse(null);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getParticipantName() {
        return Optional.ofNullable(getParticipant()).map(Participant::getName).orElse(null);
    }

    public Exercise getExercise() {
        return exercise;
    }

    public StudentParticipation exercise(Exercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    /**
     * Removes the student or team from the participation, can be invoked to make sure that sensitive information is not sent to the client.
     * E.g. tutors should not see information about the student.
     */
    public void filterSensitiveInformation() {
        setParticipant(null);
    }

    public boolean isOwnedBy(String userLogin) {
        return getStudent().map(student -> student.getLogin().equals(userLogin)).orElseGet(() -> team.hasStudentWithLogin(userLogin));
    }

    public boolean isOwnedBy(User user) {
        return isOwnedBy(user.getLogin());
    }

    @Override
    public String toString() {
        String participantString = getStudent().map(student -> "student=" + student).orElse("team=" + team);
        return "StudentParticipation{" + "id=" + getId() + ", presentationScore=" + presentationScore + ", " + participantString + "}";
    }

    /**
     * Utility method to flag whether the participation is part of an exam test run.
     * This is indicated by {@link StudentParticipation#student} equals {@link Result#getAssessor()}
     * <b>Note:</b> Test runs are only available for exams and only instructors are eligable to create test runs.
     * User permissions should therefore be checked before using this utility method.
     * Requires submissions and results to be eagerly loaded
     * @return returns whether the exam participation is a test run participation
     */
    @JsonIgnore
    public boolean isTestRunParticipation() {
        if (this.getExercise().isExamExercise() && this.getStudent().isPresent()) {
            return this.getSubmissions().stream().filter(submission -> submission.getLatestResult() != null).map(Submission::getLatestResult)
                    .anyMatch(result -> result.getAssessor() != null && result.getAssessor().equals(this.getStudent().get()));
        }
        return false;
    }

    @Override
    public Participation copyParticipationId() {
        var participation = new StudentParticipation();
        participation.setId(getId());
        return participation;
    }
}
