package de.tum.in.www1.artemis.web.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.service.exam.ExamAccessService;
import de.tum.in.www1.artemis.service.scheduled.cache.monitoring.ExamMonitoringScheduleService;

/**
 * (Websocket) controller for managing ExamActivityResource.
 */
@RestController
@Controller
public class ExamActivityResource {

    private final ExamMonitoringScheduleService examMonitoringScheduleService;

    private final ExamAccessService examAccessService;

    private final ExamRepository examRepository;

    public ExamActivityResource(ExamMonitoringScheduleService examMonitoringScheduleService, ExamAccessService examAccessService, ExamRepository examRepository) {
        this.examMonitoringScheduleService = examMonitoringScheduleService;
        this.examAccessService = examAccessService;
        this.examRepository = examRepository;
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
        return ResponseEntity.ok().body(examMonitoringScheduleService.getAllExamActions(examId));
    }

    /**
     * PUT api/exam-monitoring/{courseId}/update/{examId}: disable or enable the monitoring
     *
     * @param courseId the course to which the exam belongs to
     * @param examId the exam to which the student exams belong to
     * @param monitoring new status of the monitoring
     * @return all exam actions of the exam
     */
    @PutMapping("api/exam-monitoring/{courseId}/update/{examId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Boolean> updateMonitoring(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody boolean monitoring) {
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        Exam exam = examRepository.findByIdElseThrow(examId);
        exam.setMonitoring(monitoring);
        Exam result = examRepository.save(exam);

        if (result.isMonitoring()) {
            examMonitoringScheduleService.scheduleExamActivitySave(result.getId());
        }
        else {
            examMonitoringScheduleService.cancelExamActivitySave(result.getId());
        }
        examMonitoringScheduleService.notifyMonitoringUpdate(result.getId(), result.isMonitoring());

        return ResponseEntity.ok().body(result.isMonitoring());
    }
}
