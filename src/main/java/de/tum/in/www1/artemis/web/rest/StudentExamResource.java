package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.ExamSession;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.service.*;

/**
 * REST controller for managing ExerciseGroup.
 */
@RestController
@RequestMapping("/api")
public class StudentExamResource {

    private final Logger log = LoggerFactory.getLogger(StudentExamResource.class);

    private final ExamAccessService examAccessService;

    private final StudentExamService studentExamService;

    private final StudentExamAccessService studentExamAccessService;

    private final UserService userService;

    private final StudentExamRepository studentExamRepository;

    private final ExamSessionService examSessionService;

    public StudentExamResource(ExamAccessService examAccessService, StudentExamService studentExamService, StudentExamAccessService studentExamAccessService,
            UserService userService, StudentExamRepository studentExamRepository, ExamSessionService examSessionService) {
        this.examAccessService = examAccessService;
        this.studentExamService = studentExamService;
        this.studentExamAccessService = studentExamAccessService;
        this.userService = userService;
        this.studentExamRepository = studentExamRepository;
        this.examSessionService = examSessionService;
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/studentExams/{studentExamId} : Find a student exam by id.
     *
     * @param courseId      the course to which the student exam belongs to
     * @param examId        the exam to which the student exam belongs to
     * @param studentExamId the id of the student exam to find
     * @return the ResponseEntity with status 200 (OK) and with the found student exam as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/studentExams/{studentExamId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<StudentExam> getStudentExam(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long studentExamId) {
        log.debug("REST request to get student exam : {}", studentExamId);
        Optional<ResponseEntity<StudentExam>> accessFailure = examAccessService.checkCourseAndExamAndStudentExamAccess(courseId, examId, studentExamId);
        return accessFailure.orElseGet(() -> ResponseEntity.ok(studentExamService.findOneWithEagerExercises(studentExamId)));
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/studentExams : Get all student exams for the given exam
     *
     * @param courseId the course to which the student exams belong to
     * @param examId   the exam to which the student exams belong to
     * @return the ResponseEntity with status 200 (OK) and a list of student exams. The list can be empty
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/studentExams")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<List<StudentExam>> getStudentExamsForExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get all student exams for exam : {}", examId);
        Optional<ResponseEntity<List<StudentExam>>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccess(courseId, examId);
        return courseAndExamAccessFailure.orElseGet(() -> ResponseEntity.ok(studentExamService.findAllByExamId(examId)));
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/studentExams/conduction : Find a student exam for the user.
     * This will be used for the actual conduction of the exam. The student exam will be returned with the exercises
     * and with the student participation and with the submissions.
     *
     * @param courseId  the course to which the student exam belongs to
     * @param examId    the exam to which the student exam belongs to
     * @return the ResponseEntity with status 200 (OK) and with the found student exam as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/studentExams/conduction")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    // TODO: remove Transactional here
    @Transactional(readOnly = true)
    public ResponseEntity<StudentExam> getStudentExamForConduction(@PathVariable Long courseId, @PathVariable Long examId) {
        long start = System.currentTimeMillis();
        User currentUser = userService.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get the student exam of user {} for exam {}", currentUser.getLogin(), examId);

        Optional<ResponseEntity<StudentExam>> courseAndExamAccessFailure = studentExamAccessService.checkCourseAndExamAccess(courseId, examId, currentUser);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        Optional<StudentExam> studentExam = studentExamRepository.findWithExercisesAndStudentParticipationsAndSubmissionsAndResultByUserIdAndExamId(currentUser.getId(), examId);
        if (studentExam.isEmpty()) {
            return notFound();
        }

        // Filter attributes of exercises that should not be visible to the student
        if (studentExam.get().getExercises() != null) {
            for (Exercise exercise : studentExam.get().getExercises()) {
                if (exercise instanceof QuizExercise) {
                    // NOTE: Currently, we load the quiz questions using the Transactional mechanism above as a workaround, however this is not ideal
                    // TODO: load the quiz questions for the quiz exercise and add it to the exercise

                    var quizExercise = (QuizExercise) exercise;
                    // filterSensitiveInformation() does not work for quizzes, because then the questions won't be visible
                    // the following method makes sure that questions and other sub elements are contained (Transactional), but solution aspects are hidden
                    quizExercise.filterForStudentsDuringQuiz();

                    // TODO: also load children of the QuizSubmission in a better way, the following is a temporary workaround
                    for (var studentParticipation : quizExercise.getStudentParticipations()) {
                        // should only be one participation for one student
                        for (var submission : studentParticipation.getSubmissions()) {
                            // should only be one participation for one student
                            var quizSubmission = (QuizSubmission) submission;
                            for (var submittedAnswer : quizSubmission.getSubmittedAnswers()) {
                                if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer) {
                                    // load these objects with the transaction
                                    ((MultipleChoiceSubmittedAnswer) submittedAnswer).getSelectedOptions().size();
                                }
                                else if (submittedAnswer instanceof DragAndDropSubmittedAnswer) {
                                    // load these objects with the transaction
                                    ((DragAndDropSubmittedAnswer) submittedAnswer).getMappings().size();
                                }
                                else if (submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                                    // load these objects with the transaction
                                    ((ShortAnswerSubmittedAnswer) submittedAnswer).getSubmittedTexts().size();
                                }
                            }
                        }
                    }
                }
                else {
                    // TODO: double check if filterSensitiveInformation() is implemented correctly here for all other exercise types
                    exercise.filterSensitiveInformation();
                }
                // the exerciseGroup information is not needed
                exercise.setExerciseGroup(null);
            }
        }

        ExamSession examSession = this.examSessionService.startExamSession(studentExam.get());
        studentExam.get().setExamSessions(Set.of(examSession));

        log.info("getStudentExamForConduction done in " + (System.currentTimeMillis() - start) + "ms for " + studentExam.get().getExercises().size() + " exercises");
        return ResponseEntity.ok(studentExam.get());
    }
}
