package de.tum.in.www1.artemis.domain.exam.monitoring.actions;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;

/**
 * This action indicates whether a student started or restarted the exam.
 */
public class StartedExamAction extends ExamAction {

    /**
     * Exam session of the start/restart.
     */
    private Long sessionId;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }
}
