package de.tum.in.www1.artemis.domain.exam.monitoring.actions;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;

@Entity
@DiscriminatorValue(value = "SAVED_EXERCISE")
public class SavedExerciseAction extends ExamAction {

    @Column(name = "forced", nullable = false)
    private boolean forced;

    @Column(name = "failed", nullable = false)
    private boolean failed;

    @Column(name = "automatically", nullable = false)
    private boolean automatically;

    @OneToOne
    @JoinColumn(name = "submission_id")
    private Submission submission;

    public boolean isForced() {
        return forced;
    }

    public boolean isFailed() {
        return failed;
    }

    public boolean isAutomatically() {
        return automatically;
    }

    public Submission getSubmission() {
        return submission;
    }
}
