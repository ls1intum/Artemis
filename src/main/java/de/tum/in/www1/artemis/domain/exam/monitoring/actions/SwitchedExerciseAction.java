package de.tum.in.www1.artemis.domain.exam.monitoring.actions;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;

@Entity
@DiscriminatorValue(value = "SWITCHED_EXERCISE")
public class SwitchedExerciseAction extends ExamAction {

    @OneToOne
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    public Exercise getExercise() {
        return exercise;
    }
}
