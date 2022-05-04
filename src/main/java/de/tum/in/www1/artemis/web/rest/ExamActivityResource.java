package de.tum.in.www1.artemis.web.rest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.exam.StudentExamAccessService;
import de.tum.in.www1.artemis.service.exam.monitoring.ExamActionService;
import de.tum.in.www1.artemis.service.exam.monitoring.ExamActivityService;

public class ExamActivityResource {

    private final Logger log = LoggerFactory.getLogger(ExamActivityResource.class);

    private final ExamActionService examActionService;

    private final ExamActivityService examActivityService;

    private final StudentExamAccessService studentExamAccessService;

    // TODO replace with Service
    private final StudentExamRepository studentExamRepository;

    // TODO replace with Service
    private final UserRepository userRepository;

    public ExamActivityResource(ExamActionService examActionService, ExamActivityService examActivityService, StudentExamAccessService studentExamAccessService,
            StudentExamRepository studentExamRepository, UserRepository userRepository) {
        this.examActionService = examActionService;
        this.examActivityService = examActivityService;
        this.studentExamAccessService = studentExamAccessService;
        this.studentExamRepository = studentExamRepository;
        this.userRepository = userRepository;
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
        StudentExam studentExam = studentExamRepository.findByIdWithExercisesElseThrow(studentExamId);
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        boolean isTestRun = studentExam.isTestRun();
        this.studentExamAccessService.checkStudentExamAccessElseThrow(courseId, examId, studentExamId, currentUser, isTestRun);

        // TODO: Filter not valid actions
        // List<ExamAction> examActions = actions.stream().map(examActionService::mapExamAction).toList();
        actions.forEach(action -> action.setExamActivity(studentExam.getExamActivity()));
        examActionService.saveAll(actions);

        studentExam.getExamActivity().addExamActions(actions);
        examActivityService.save(studentExam.getExamActivity());

        // TODO: Update log
        log.info("REST request by user: {} for exam with id {} to add {} actions to student-exam {}", currentUser.getLogin(), examId, 5, studentExamId);

        return ResponseEntity.ok().build();
    }
}
