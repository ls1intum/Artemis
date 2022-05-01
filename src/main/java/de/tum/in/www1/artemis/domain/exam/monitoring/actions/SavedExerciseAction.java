package de.tum.in.www1.artemis.domain.exam.monitoring.actions;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;

@Entity
@DiscriminatorValue("SAVED_EXERCISE")
public class SavedExerciseAction extends ExamAction {

    @Column(name = "forced")
    private boolean forced;

    @Column(name = "failed")
    private boolean failed;

    @Column(name = "automatically")
    private boolean automatically;

    @OneToOne
    @JoinColumn(name = "submission_id")
    private Submission submission;

    public SavedExerciseAction(boolean forced, boolean failed, boolean automatically, Submission submission) {
        this.forced = forced;
        this.failed = failed;
        this.automatically = automatically;
        this.submission = submission;
    }

    public SavedExerciseAction() {
    }

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

    public Submission getSubmission() {
        return submission;
    }

    public void setSubmission(Submission submission) {
        this.submission = submission;
    }
}
