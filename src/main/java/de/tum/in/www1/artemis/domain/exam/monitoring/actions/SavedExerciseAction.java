package de.tum.in.www1.artemis.domain.exam.monitoring.actions;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;

/**
 * This action indicates whether a student saved an exercise manually or automatically.
 */
public class SavedExerciseAction extends ExamAction {

    /**
     * This boolean indicates whether a save operation was forced or not.
     */
    private boolean forced;

    /**
     * This boolean indicates whether a save was successful or not.
     */
    private boolean failed;

    /**
     * This boolean indicates whether a save was performed manually or automatically.
     */
    private boolean automatically;

    /**
     * The corresponding submission id.
     */
    private Long submissionId;

    /**
     * The corresponding exercise id.
     */
    private Long exerciseId;

    public boolean isForced() {
        return forced;
    }

    public void setForced(boolean forced) {
        this.forced = forced;
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public boolean isAutomatically() {
        return automatically;
    }

    public void setAutomatically(boolean automatically) {
        this.automatically = automatically;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(Long submissionId) {
        this.submissionId = submissionId;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }
}
