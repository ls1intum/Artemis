package de.tum.in.www1.artemis.domain.participation;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.view.QuizView;

@Entity
@DiscriminatorValue(value = "SP")
public class StudentParticipation extends ParticipantParticipation {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private User student;

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

    /**
     * Removes the student from the participation, can be invoked to make sure that sensitive information is not sent to the client.
     * E.g. tutors should not see information about the student.
     */
    @Override
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
