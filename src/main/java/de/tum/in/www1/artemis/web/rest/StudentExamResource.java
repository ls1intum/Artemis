package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.ExamSession;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
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

    private final QuizExerciseService quizExerciseService;

    private final ParticipationService participationService;

    public StudentExamResource(ExamAccessService examAccessService, StudentExamService studentExamService, StudentExamAccessService studentExamAccessService,
            UserService userService, StudentExamRepository studentExamRepository, ExamSessionService examSessionService, ParticipationService participationService,
            QuizExerciseService quizExerciseService) {
        this.examAccessService = examAccessService;
        this.studentExamService = studentExamService;
        this.studentExamAccessService = studentExamAccessService;
        this.userService = userService;
        this.studentExamRepository = studentExamRepository;
        this.examSessionService = examSessionService;
        this.participationService = participationService;
        this.quizExerciseService = quizExerciseService;
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
    public ResponseEntity<StudentExam> getStudentExamForConduction(@PathVariable Long courseId, @PathVariable Long examId) {
        long start = System.currentTimeMillis();
        User currentUser = userService.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get the student exam of user {} for exam {}", currentUser.getLogin(), examId);

        Optional<ResponseEntity<StudentExam>> courseAndExamAccessFailure = studentExamAccessService.checkCourseAndExamAccess(courseId, examId, currentUser);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        // 1st: load the studentExam with all associated exercises
        Optional<StudentExam> optionalStudentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(currentUser.getId(), examId);
        if (optionalStudentExam.isEmpty()) {
            return notFound();
        }
        var studentExam = optionalStudentExam.get();

        // we reload the quiz exercise because we also need the quiz questions and it is not possible to load them in a generic way with the entity graph used in
        // studentExamRepository.findWithExercisesByUserIdAndExamId
        for (int i = 0; i < studentExam.getExercises().size(); i++) {
            var exercise = studentExam.getExercises().get(i);
            if (exercise instanceof QuizExercise) {
                // reload and replace the quiz exercise
                var quizExercise = quizExerciseService.findOneWithQuestions(exercise.getId());
                quizExercise.filterForStudentsDuringQuiz();
                studentExam.getExercises().set(i, quizExercise);
            }
        }

        // 2nd: fetch participations, submissions and results for these exercises, note: exams only contain individual exercises for now
        // fetching all participations at once is more effective
        List<StudentParticipation> participations = participationService.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(currentUser.getId(),
                studentExam.getExercises());

        // 3rd: connect the exercises and student participations correctly and make sure all relevant associations are available
        for (Exercise exercise : studentExam.getExercises()) {
            // add participation with submission and result to each exercise
            filterForExam(exercise, participations);

            // Filter attributes of exercises that should not be visible to the student
            // Note: sensitive information for quizzes was already removed in the for loop above
            if (!(exercise instanceof QuizExercise)) {
                // TODO: double check if filterSensitiveInformation() is implemented correctly here for all other exercise types
                exercise.filterSensitiveInformation();
            }
        }

        ExamSession examSession = this.examSessionService.startExamSession(studentExam);
        studentExam.setExamSessions(Set.of(examSession));

        // not needed
        studentExam.getExam().setCourse(null);

        log.info("getStudentExamForConduction done in " + (System.currentTimeMillis() - start) + "ms for " + studentExam.getExercises().size() + " exercises for user "
                + currentUser.getLogin());
        return ResponseEntity.ok(studentExam);
    }

    /**
     * Find the participation in participations that belongs to the given exercise that includes the exercise data
     *
     * @param exercise the exercise for which the user participation should be filtered
     * @param participations the set of participations, wherein to search for the relevant participation
     */
    public void filterForExam(Exercise exercise, List<StudentParticipation> participations) {
        // remove the unnecessary inner course attribute
        exercise.setCourse(null);
        exercise.setExerciseGroup(null);

        if (exercise instanceof ProgrammingExercise) {
            var programmingExercise = (ProgrammingExercise) exercise;
            programmingExercise.setTestRepositoryUrl(null);
        }

        // get user's participation for the exercise
        StudentParticipation participation = participations != null ? exercise.findRelevantParticipation(participations) : null;

        // add relevant submission (relevancy depends on InitializationState) with its result to participation
        if (participation != null) {
            // remove inner exercise from participation
            participation.setExercise(null);
            // only include the latest submission
            Optional<Submission> latestSubmission = participation.findLatestSubmission();
            if (latestSubmission.isPresent()) {
                participation.setSubmissions(Set.of(latestSubmission.get()));
                // Set the latest result into the participation as the client expects it there for programming exercises
                Result result = latestSubmission.get().getResult();
                if (result != null) {
                    participation.setResults(Set.of(result));
                }
            }
            // add participation into an array
            exercise.setStudentParticipations(Set.of(participation));
        }
    }
}
