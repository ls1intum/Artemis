package de.tum.in.www1.artemis.domain.lecture_module;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.Exercise;

@Entity
@DiscriminatorValue(value = "E")
public class ExerciseModule extends LectureModule {

    @ManyToMany(fetch = FetchType.EAGER)
    @OrderColumn(name = "exercise_order")
    @JoinTable(name = "exercise_module_exercise", joinColumns = @JoinColumn(name = "exercise_module_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "exercise_id", referencedColumnName = "id"))
    private List<Exercise> exercises = new ArrayList<>();

    public List<Exercise> getExercises() {
        return exercises;
    }

    public void setExercises(List<Exercise> exercises) {
        this.exercises = exercises;
    }

    public ExerciseModule addExercise(Exercise exercise) {
        this.exercises.add(exercise);
        return this;
    }

}
