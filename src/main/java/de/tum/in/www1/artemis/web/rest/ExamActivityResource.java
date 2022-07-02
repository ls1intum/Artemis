package de.tum.in.www1.artemis.web.rest;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.enumeration.ExamActionType;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.domain.exam.monitoring.actions.StartedExamAction;
import de.tum.in.www1.artemis.service.scheduled.cache.monitoring.ExamMonitoringScheduleService;

/**
 * (Websocket) controller for managing ExamActivityResource.
 */
@RestController
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

    /**
     * GET api/exam-monitoring/{examId}/load-actions: returns all actions of the exam.
     *
     * @param examId the exam to which the student exams belong to
     * @return all exam actions of the exam
     */
    @GetMapping("api/exam-monitoring/{examId}/load-actions")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<ExamAction>> loadAllActions(@PathVariable Long examId) {
        var actions = examMonitoringScheduleService.getAllExamActions(examId);
        if (actions.isEmpty()) {
            var generatedActions = new ArrayList<ExamAction>();
            for (int i = 0; i < 1000000; i++) {
                var action = new StartedExamAction();
                action.setSessionId(0L);
                action.setStudentExamId(17L);
                action.setExamActivityId(17L);
                action.setType(ExamActionType.STARTED_EXAM);
                action.setTimestamp(ZonedDateTime.now().plus(1, ChronoUnit.MINUTES));
                generatedActions.add(action);
            }
            examMonitoringScheduleService.addExamActions(examId, generatedActions);
            actions = examMonitoringScheduleService.getAllExamActions(examId);
        }
        return ResponseEntity.ok().body(actions);
    }
}
