package de.tum.in.www1.artemis.domain.exam.monitoring.actions;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

import de.tum.in.www1.artemis.domain.exam.ExamSession;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;

@Entity
@DiscriminatorValue("STARTED_EXAM")
public class StartedExamAction extends ExamAction {

    @OneToOne
    @JoinColumn(name = "session_id")
    private ExamSession examSession;

    public StartedExamAction(ExamSession examSession) {
        this.examSession = examSession;
    }

    public StartedExamAction() {
    }

    public ExamSession getExamSession() {
        return examSession;
    }

    public void setExamSession(ExamSession examSession) {
        this.examSession = examSession;
    }
}
