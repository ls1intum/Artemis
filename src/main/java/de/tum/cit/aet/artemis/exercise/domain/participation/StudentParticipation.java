package de.tum.cit.aet.artemis.exercise.domain.participation;

import java.util.Optional;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Team;

@Entity
@DiscriminatorValue(value = "SP")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StudentParticipation extends Participation {

    @Column(name = "presentation_score")
    private Double presentationScore;

    @ManyToOne
    private User student;

    @ManyToOne
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

    /** The discriminator the client reads to tell participation kinds apart. */
    public static final String TYPE = "student";

    /**
     * A stand-in carrying nothing but the id, for writing a submission's foreign key.
     * <p>
     * The submit path resolves its participation as a projection and must not load the entity - doing so pulls the
     * exercise, its course and, for an exam exercise, the exercise group with its exam and that exam's course along.
     * <p>
     * Never read from this and never hand it to anything but a save. What the response reports is mapped from the
     * projection instead, and everything the save needs from the participation is the id.
     *
     * @param id the id of the participation the submission belongs to
     * @return a detached stand-in carrying only that id
     */
    public static StudentParticipation idOnlyReference(long id) {
        StudentParticipation reference = new StudentParticipation();
        reference.setId(id);
        return reference;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * allows to set the participant independent whether it is a team or user
     *
     * @param participant either a team or user
     */
    public void setParticipant(Participant participant) {
        switch (participant) {
            case User user -> this.student = user;
            case Team team1 -> this.team = team1;
            case null -> {
                this.student = null;
                if (this.team != null) {
                    this.team.setStudents(null);
                }
            }
            default -> throw new Error("Unknown participant type");
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
