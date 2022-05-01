package de.tum.in.www1.artemis.service.dto.exam.monitoring.actions;

import de.tum.in.www1.artemis.service.dto.exam.monitoring.ExamActionDTO;

public class SavedExerciseActionDTO extends ExamActionDTO {

    private boolean forced;

    private boolean failed;

    private boolean automatically;

    private long submissionId;

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

    public long getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(long submissionId) {
        this.submissionId = submissionId;
    }
}
