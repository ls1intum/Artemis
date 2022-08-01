package de.tum.in.www1.artemis.domain.exam.statistics.actions;

import de.tum.in.www1.artemis.domain.exam.statistics.ExamAction;

/**
 * This action indicates whether a student switched to another exercise or to the overview page.
 */
public class SwitchedExerciseAction extends ExamAction {

    /**
     * Corresponding exercise id or null (overview page).
     */
    private Long exerciseId;

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }
}
