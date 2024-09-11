package de.tum.cit.aet.artemis.domain.participation;

import java.util.Optional;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.Team;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.view.QuizView;

@Entity
@DiscriminatorValue(value = "SP")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StudentParticipation extends Participation {

    @Column(name = "presentation_score")
    private Double presentationScore;

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private User student;

    @ManyToOne
    @JsonView(QuizView.Before.class)
    private Team team;

    public Double getPresentationScore() {
        return presentationScore;
    }

    public void setPresentationScore(Double presentationScore) {
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

    @Override
    public String getType() {
        return "student";
    }

    /**
     * allows to set the participant independent whether it is a team or user
     *
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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getParticipantIdentifier() {
        return Optional.ofNullable(getParticipant()).map(Participant::getParticipantIdentifier).orElse(null);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getParticipantName() {
        return Optional.ofNullable(getParticipant()).map(Participant::getName).orElse(null);
    }

    @Override
    public Exercise getExercise() {
        return exercise;
    }

    public StudentParticipation exercise(Exercise exercise) {
        this.exercise = exercise;
        return this;
    }

    @Override
    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    /**
     * Removes the student or team from the participation, can be invoked to make sure that sensitive information is not sent to the client.
     * E.g. tutors should not see information about the student.
     */
    @Override
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
        return getClass().getSimpleName() + "{" + "id=" + getId() + ", presentationScore=" + presentationScore + ", " + participantString + "}";
    }

}
