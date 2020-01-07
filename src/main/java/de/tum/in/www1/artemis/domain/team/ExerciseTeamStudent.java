package de.tum.in.www1.artemis.domain.team;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;

@Entity
@DiscriminatorValue(value = "ETS")
public class ExerciseTeamStudent extends TeamStudent {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    private Exercise exercise;

    public ExerciseTeamStudent() {
        super();
    }

    public ExerciseTeamStudent(ExerciseTeam exerciseTeam, User user) {
        super(exerciseTeam, user);
        this.exercise = exerciseTeam.getExercise();
    }

    public String toString() {
        return "ExerciseTeamStudent{" + "id=" + getId() + ", exercise=" + exercise + ", team=" + getTeam() + ", student=" + getStudent() + '}';
    }
}
