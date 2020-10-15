package de.tum.in.www1.artemis.domain.lecture_module;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.Exercise;

@Entity
@DiscriminatorValue("E")
public class ExerciseModule extends LectureModule {

    @Column(name = "description")
    @Lob
    private String description;

    @ManyToOne
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
