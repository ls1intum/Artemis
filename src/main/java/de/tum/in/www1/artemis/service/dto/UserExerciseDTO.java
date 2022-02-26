package de.tum.in.www1.artemis.service.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;

/**
 * DTO for sending exercises and user who wants to access those exercises
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserExerciseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Set<Exercise> exercises;

    private User user;

    public UserExerciseDTO() {
        /* Needed from the object mapper in order to construct the object */}

    public UserExerciseDTO(Set<Exercise> exercises, User user) {
        this.exercises = exercises;
        this.user = user;
    }

    public Set<Exercise> getExercises() {
        return exercises;
    }

    public void setExercises(Set<Exercise> exercises) {
        this.exercises = exercises;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public String toString() {
        return String.format("UserExerciseDTO{exercises=%s, user=%s}", getExercises(), getUser());
    }
}
