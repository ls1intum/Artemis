package de.tum.in.www1.artemis.web.rest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.service.exam.StudentExamAccessService;
import de.tum.in.www1.artemis.service.scheduled.cache.monitoring.ExamMonitoringScheduleService;

/**
 * REST controller for managing ExamActivityResource.
 */
@RestController
@RequestMapping("/api")
public class ExamActivityResource {

    private final Logger log = LoggerFactory.getLogger(ExamActivityResource.class);

    private final StudentExamAccessService studentExamAccessService;

    // TODO replace with Service
    private final StudentExamRepository studentExamRepository;

    // TODO replace with Service
    private final UserRepository userRepository;

    private final ExamMonitoringScheduleService examMonitoringScheduleService;

    private final ExamService examService;

    public ExamActivityResource(StudentExamAccessService studentExamAccessService, StudentExamRepository studentExamRepository, UserRepository userRepository,
            ExamMonitoringScheduleService examMonitoringScheduleService, ExamService examService) {
        this.examMonitoringScheduleService = examMonitoringScheduleService;
        this.studentExamAccessService = studentExamAccessService;
        this.studentExamRepository = studentExamRepository;
        this.userRepository = userRepository;
        this.examService = examService;
    }

    /**
     * PUT /courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/actions: Adds the performed actions by
     * the user
     *
     * @param courseId      the course to which the student exams belong to
     * @param examId        the exam to which the student exams belong to
     * @param studentExamId the student exam id where we want to add the actions
     * @param actions       list of actions performed by the user after the last synchronisation
     * @return 200 if successful
     */
    @PutMapping("/courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/actions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<StudentExam> updatePerformedExamActions(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long studentExamId,
            @RequestBody List<ExamAction> actions) {
        Exam exam = examService.findByIdOrElseThrow(examId);
        if (!exam.isMonitoring())
            return ResponseEntity.badRequest().build();

        StudentExam studentExam = studentExamRepository.findByIdWithExercisesElseThrow(studentExamId);
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        boolean isTestRun = studentExam.isTestRun();
        this.studentExamAccessService.checkStudentExamAccessElseThrow(courseId, examId, studentExamId, currentUser, isTestRun);

        // TODO: Add array
        actions.forEach(action -> examMonitoringScheduleService.addExamAction(examId, studentExamId, action));
        log.info("REST request by user: {} for exam with id {} to add {} actions to student-exam {}", currentUser.getLogin(), examId, 5, studentExamId);

        return ResponseEntity.ok().build();
    }
}
