package de.tum.in.www1.artemis.domain.exam.monitoring.actions;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;

/**
 * This action indicates whether a student switched to another exercise or to the overview page.
 */
@Entity
@DiscriminatorValue("SWITCHED_EXERCISE")
public class SwitchedExerciseAction extends ExamAction {

    /**
     * Corresponding exercise id or null (overview page).
     */
    @Column(name = "exercise_id")
    private Long exerciseId;

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }
}
