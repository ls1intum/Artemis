package de.tum.in.www1.artemis.service.dto.exam.monitoring.actions;

import de.tum.in.www1.artemis.service.dto.exam.monitoring.ExamActionDTO;

public class StartedExamActionDTO extends ExamActionDTO {

    private long examSessionId;

    public long getExamSessionId() {
        return examSessionId;
    }

    public void setExamSessionId(long examSessionId) {
        this.examSessionId = examSessionId;
    }
}
