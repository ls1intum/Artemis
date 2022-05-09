package de.tum.in.www1.artemis.domain.exam.monitoring.actions;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;

/**
 * This action indicates whether a student started or restarted the exam.
 */
@Entity
@DiscriminatorValue("STARTED_EXAM")
public class StartedExamAction extends ExamAction {

    /**
     * Exam session of the start/restart.
     */
    @JoinColumn(name = "session_id")
    private Long sessionId;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }
}
