package de.tum.in.www1.artemis.domain.team;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;

@Entity
@DiscriminatorValue(value = "ET")
public class ExerciseTeam extends Team {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    private Exercise exercise;

    public Exercise getExercise() {
        return exercise;
    }

    public ExerciseTeam exercise(Exercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Team addStudents(User user) {
        ExerciseTeamStudent exerciseTeamStudent = new ExerciseTeamStudent(this, user);
        this.students.add(exerciseTeamStudent);
        return this;
    }

    public String toString() {
        return "ExerciseTeam{" + "id=" + getId() + ", exercise=" + exercise + '}';
    }
}
