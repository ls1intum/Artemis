package de.tum.in.www1.artemis.domain.participation;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.view.QuizView;

@Entity
@DiscriminatorValue(value = "SP")
public class StudentParticipation extends Participation implements AgentParticipation {

    private static final long serialVersionUID = 1L;

    @Column(name = "presentation_score")
    private Integer presentationScore;

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private User student;

    public Integer getPresentationScore() {
        return presentationScore;
    }

    public StudentParticipation presentationScore(Integer presentationScore) {
        this.presentationScore = presentationScore;
        return this;
    }

    public void setPresentationScore(Integer presentationScore) {
        this.presentationScore = presentationScore;
    }

    public User getStudent() {
        return student;
    }

    public Participation student(User user) {
        this.student = user;
        return this;
    }

    public void setStudent(User user) {
        this.student = user;
    }

    public User getAgent() {
        return student;
    }

    public void setAgent(Agent agent) {
        this.student = (User) agent;
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
     * Removes the student from the participation, can be invoked to make sure that sensitive information is not sent to the client.
     * E.g. tutors should not see information about the student.
     */
    public void filterSensitiveInformation() {
        setStudent(null);
    }

    @Override
    public String toString() {
        return "StudentParticipation{" + "id=" + getId() + ", presentationScore=" + presentationScore + ", student=" + student + '}';
    }

    @Override
    public Participation copyParticipationId() {
        var participation = new StudentParticipation();
        participation.setId(getId());
        return participation;
    }
}
