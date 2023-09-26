package de.tum.in.www1.artemis.domain.iris.session;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;

/**
 * An IrisExerciseSession represents a conversation between an exercise instructor and Iris
 * for the purpose of editing a programming exercise.
 */
@Entity
@DiscriminatorValue("EXERCISE")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisExerciseSession extends IrisSession {

    @ManyToOne
    @JsonIgnore
    private ProgrammingExercise exercise;

    @ManyToOne
    @JsonIgnore
    private User user;

    public ProgrammingExercise getExercise() {
        return exercise;
    }

    public void setExercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public String toString() {
        return "IrisExerciseSession{"
                + "id=" + getId()
                + ", exercise=" + (exercise == null ? "null" : exercise.getId())
                + ", user=" + (user == null ? "null" : user.getName())
                + '}';
    }
}
