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
import de.tum.in.www1.artemis.domain.exam.statistics.ExamAction;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.service.exam.ExamAccessService;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.scheduled.cache.statistics.ExamLiveStatisticsScheduleService;

/**
 * (Websocket) controller for managing ExamActivityResource.
 */
@RestController
@Controller
public class ExamActivityResource {

    private final ExamLiveStatisticsScheduleService examLiveStatisticsScheduleService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final ExamAccessService examAccessService;

    private final ExamRepository examRepository;

    public ExamActivityResource(ExamLiveStatisticsScheduleService examLiveStatisticsScheduleService, InstanceMessageSendService instanceMessageSendService,
            ExamAccessService examAccessService, ExamRepository examRepository) {
        this.examLiveStatisticsScheduleService = examLiveStatisticsScheduleService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.examAccessService = examAccessService;
        this.examRepository = examRepository;
    }

    /**
     * Adds the performed actions by the user into the cache. This method does not perform any authorization and validation checks.
     *
     * @param examId    the exam to which the student exams belong to
     * @param action    action performed by the user
     */
    @MessageMapping("/topic/exams/{examId}/live-statistics-actions")
    public void updatePerformedExamActions(@DestinationVariable Long examId, @Payload ExamAction action) {
        examLiveStatisticsScheduleService.addExamActions(examId, action);
    }

    /**
     * GET api/exams/{examId}/load-actions: returns all actions of the exam.
     *
     * @param examId the exam to which the student exams belong to
     * @return all exam actions of the exam
     */
    @GetMapping("api/exams/{examId}/load-actions")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<ExamAction>> loadAllActions(@PathVariable Long examId) {
        return ResponseEntity.ok().body(examLiveStatisticsScheduleService.getAllExamActions(examId));
    }

    /**
     * PUT api/courses/{courseId}/exams/{examId}/statistics: disable or enable the exam live statistics
     *
     * @param courseId the course to which the exam belongs to
     * @param examId the exam to which the student exams belong to
     * @param liveStatistics new status of the liveStatistics
     * @return all exam actions of the exam
     */
    @PutMapping("api/courses/{courseId}/exams/{examId}/statistics")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Boolean> updateLiveStatistics(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody boolean liveStatistics) {
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        Exam exam = examRepository.findByIdElseThrow(examId);
        exam.setLiveStatistics(liveStatistics);
        Exam result = examRepository.save(exam);

        if (result.isLiveStatistics()) {
            instanceMessageSendService.sendExamLiveStatisticsSchedule(result.getId());
        }
        else {
            instanceMessageSendService.sendExamLiveStatisticsScheduleCancel(result.getId());
        }
        examLiveStatisticsScheduleService.notifyExamLiveStatisticsUpdate(result.getId(), result.isLiveStatistics());

        return ResponseEntity.ok().body(result.isLiveStatistics());
    }
}
