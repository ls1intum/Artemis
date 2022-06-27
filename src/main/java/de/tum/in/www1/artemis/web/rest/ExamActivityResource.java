package de.tum.in.www1.artemis.web.rest;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.service.scheduled.cache.monitoring.ExamMonitoringScheduleService;

/**
 * Websocket controller for managing ExamActivityResource.
 */
@Controller
public class ExamActivityResource {

    private final ExamMonitoringScheduleService examMonitoringScheduleService;

    public ExamActivityResource(ExamMonitoringScheduleService examMonitoringScheduleService) {
        this.examMonitoringScheduleService = examMonitoringScheduleService;
    }

    /**
     * Adds the performed actions by the user into the cache. This method does not perform any authorization and validation checks.
     *
     * @param examId    the exam to which the student exams belong to
     * @param action    action performed by the user
     */
    @MessageMapping("/topic/exam-monitoring/{examId}/actions")
    public void updatePerformedExamActions(@DestinationVariable Long examId, @Payload ExamAction action) {
        examMonitoringScheduleService.addExamActions(examId, action);
    }
}
