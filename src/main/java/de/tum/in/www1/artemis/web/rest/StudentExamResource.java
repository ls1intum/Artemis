package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.EXAM_START_WAIT_TIME_MINUTES;
import static de.tum.in.www1.artemis.service.util.TimeLogUtil.formatDurationFrom;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExamSession;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.exam.*;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.util.ExamExerciseStartPreparationStatus;
import de.tum.in.www1.artemis.service.util.HttpRequestUtils;
import de.tum.in.www1.artemis.web.rest.dto.StudentExamWithGradeDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

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

    private final UserRepository userRepository;

    private final AuditEventRepository auditEventRepository;

    private final StudentExamRepository studentExamRepository;

    private final ExamDateService examDateService;

    private final ExamSessionService examSessionService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ExamRepository examRepository;

    private final SubmittedAnswerRepository submittedAnswerRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ExamService examService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final WebsocketMessagingService messagingService;

    @Value("${info.student-exam-store-session-data:#{true}}")
    private boolean storeSessionDataInStudentExamSession;

    public StudentExamResource(ExamAccessService examAccessService, StudentExamService studentExamService, StudentExamAccessService studentExamAccessService,
            UserRepository userRepository, AuditEventRepository auditEventRepository, StudentExamRepository studentExamRepository, ExamDateService examDateService,
            ExamSessionService examSessionService, StudentParticipationRepository studentParticipationRepository, QuizExerciseRepository quizExerciseRepository,
            ExamRepository examRepository, SubmittedAnswerRepository submittedAnswerRepository, AuthorizationCheckService authorizationCheckService, ExamService examService,
            InstanceMessageSendService instanceMessageSendService, WebsocketMessagingService messagingService) {
        this.examAccessService = examAccessService;
        this.studentExamService = studentExamService;
        this.studentExamAccessService = studentExamAccessService;
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;
        this.studentExamRepository = studentExamRepository;
        this.examDateService = examDateService;
        this.examSessionService = examSessionService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.examRepository = examRepository;
        this.submittedAnswerRepository = submittedAnswerRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.examService = examService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.messagingService = messagingService;
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/student-exams/{studentExamId} : Find a student exam by id.
     * Includes aggregate points, assessment result and grade calculations if the exam is assessed.
     * See {@link StudentExamWithGradeDTO} for more explanation.
     *
     * @param courseId      the course to which the student exam belongs to
     * @param examId        the exam to which the student exam belongs to
     * @param studentExamId the id of the student exam to find
     * @return the ResponseEntity with status 200 (OK) and with the found student exam as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/student-exams/{studentExamId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<StudentExamWithGradeDTO> getStudentExam(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long studentExamId) {
        log.debug("REST request to get student exam : {}", studentExamId);

        examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(courseId, examId, studentExamId);

        StudentExam studentExam = studentExamRepository.findByIdWithExercisesElseThrow(studentExamId);

        loadQuizExercisesForStudentExam(studentExam);

        // fetch participations, submissions and results for these exercises, note: exams only contain individual exercises for now
        // fetching all participations at once is more effective
        List<StudentParticipation> participations = studentParticipationRepository.findByStudentExamWithEagerSubmissionsResult(studentExam, true);

        // fetch all submitted answers for quizzes
        submittedAnswerRepository.loadQuizSubmissionsSubmittedAnswers(participations);

        // connect the exercises and student participations correctly and make sure all relevant associations are available
        for (Exercise exercise : studentExam.getExercises()) {
            // add participation with submission and result to each exercise
            filterParticipationForExercise(studentExam, exercise, participations, true);
        }
        studentExam.getUser().setVisibleRegistrationNumber();

        StudentExamWithGradeDTO studentExamWithGradeDTO = examService.calculateStudentResultWithGradeAndPoints(studentExam, participations);

        return ResponseEntity.ok(studentExamWithGradeDTO);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/student-exams : Get all student exams for the given exam
     *
     * @param courseId the course to which the student exams belong to
     * @param examId   the exam to which the student exams belong to
     * @return the ResponseEntity with status 200 (OK) and a set of student exams. The set can be empty
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/student-exams")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Set<StudentExam>> getStudentExamsForExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get all student exams for exam : {}", examId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        return ResponseEntity.ok(studentExamRepository.findByExamId(examId));
    }

    /**
     * PATCH /courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/working-time : Update the working time of the student exam
     *
     * @param courseId      the course to which the student exams belong to
     * @param examId        the exam to which the student exams belong to
     * @param studentExamId the id of the student exam to find
     * @param workingTime   the new working time in seconds
     * @return the ResponseEntity with status 200 (OK) and with the updated student exam as body
     */
    @PatchMapping("/courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/working-time")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<StudentExam> updateWorkingTime(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long studentExamId,
            @RequestBody Integer workingTime) {
        log.debug("REST request to update the working time of student exam : {}", studentExamId);

        examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(courseId, examId, studentExamId);

        if (workingTime <= 0) {
            throw new BadRequestException();
        }
        StudentExam studentExam = studentExamRepository.findByIdWithExercisesElseThrow(studentExamId);
        if (!studentExam.isTestRun()) {
            Exam exam = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(examId);
            // when the exam is already visible, the working time cannot be changed, due to permission issues with unlock and lock operations for programming exercises
            if (ZonedDateTime.now().isAfter(exam.getVisibleDate())) {
                throw new BadRequestAlertException("Working time can not be changed after exam becomes visible", "StudentExam", "workingTimeError");
            }
            if (ZonedDateTime.now().isBefore(examDateService.getLatestIndividualExamEndDate(exam)) && exam.getStartDate() != null
                    && ZonedDateTime.now().isBefore(exam.getStartDate().plusSeconds(workingTime))) {
                examService.scheduleModelingExercises(exam);
            }

        }

        studentExam.setWorkingTime(workingTime);
        var savedStudentExam = studentExamRepository.save(studentExam);

        instanceMessageSendService.sendExamLiveStatisticsSchedule(examId);

        return ResponseEntity.ok(savedStudentExam);
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/student-exams/submit : Submits the student exam
     * Updates all submissions and marks student exam as submitted according to given student exam
     * NOTE: the studentExam has to be sent with all exercises, participations and submissions
     *
     * @param courseId    the course to which the student exams belong to
     * @param examId      the exam to which the student exams belong to
     * @param studentExam the student exam with exercises, participations and submissions
     * @return empty response with status code:
     * 200 if successful
     * 400 if student exam was in an illegal state
     */
    @PostMapping("/courses/{courseId}/exams/{examId}/student-exams/submit")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<StudentExam> submitStudentExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody StudentExam studentExam) {
        log.debug("REST request to mark the studentExam as submitted : {}", studentExam.getId());

        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        // prevent manipulation of the user object that is attached to the student exam in the request body (which is saved later on into the database as part of this request)
        if (!Objects.equals(studentExam.getUser().getId(), currentUser.getId())) {
            throw new AccessForbiddenException();
        }

        StudentExam existingStudentExam = studentExamRepository.findByIdWithExercisesElseThrow(studentExam.getId());
        this.studentExamAccessService.checkStudentExamAccessElseThrow(courseId, examId, existingStudentExam, currentUser);

        if (Boolean.TRUE.equals(studentExam.isSubmitted()) || Boolean.TRUE.equals(existingStudentExam.isSubmitted())) {
            log.error("Student exam with id {} for user {} is already submitted.", studentExam.getId(), currentUser.getLogin());
            throw new ConflictException("You have already submitted.", "studentExam", "alreadySubmitted");
        }

        // checks if student exam is live (after start date, before end date + grace period)
        if (!existingStudentExam.isTestRun() && (existingStudentExam.getExam().getStartDate() != null && !ZonedDateTime.now().isAfter(existingStudentExam.getExam().getStartDate())
                || existingStudentExam.getIndividualEndDate() != null && !ZonedDateTime.now().isBefore(existingStudentExam.getIndividualEndDateWithGracePeriod()))) {
            throw new AccessForbiddenException("You can only submit between start and end of the exam.");
        }

        messagingService.sendMessage("/topic/exam/" + examId + "/submitted", "");

        return studentExamService.submitStudentExam(existingStudentExam, studentExam, currentUser);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/conduction : Find the specified student exam for the user.
     * This will be used for the actual conduction of the exam. The student exam will be returned with the exercises
     * and with the student participation and with the submissions.
     * NOTE: when this is called it will also mark the student exam as started
     *
     * @param courseId      the course to which the student exam belongs to
     * @param examId        the exam to which the student exam belongs to
     * @param studentExamId the studentExam which should be loaded
     * @param request       the http request, used to extract headers
     * @return the ResponseEntity with status 200 (OK) and with the found student exam as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/conduction")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<StudentExam> getStudentExamForConduction(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long studentExamId,
            HttpServletRequest request) {
        long start = System.currentTimeMillis();
        User user = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get the student exam of user {} for exam {}", user.getLogin(), examId);

        StudentExam studentExam = studentExamRepository.findByIdWithExercisesElseThrow(studentExamId);

        if (!user.equals(studentExam.getUser())) {
            throw new AccessForbiddenException("Current user is not the user of the requested student exam");
        }
        studentExamAccessService.checkCourseAndExamAccessElseThrow(courseId, examId, user, studentExam.isTestRun(), false);

        // students can not fetch the exam until EXAM_START_WAIT_TIME_MINUTES minutes before the exam start, we use the same constant in the client
        if (ZonedDateTime.now().plusMinutes(EXAM_START_WAIT_TIME_MINUTES).isBefore(studentExam.getExam().getStartDate())) {
            throw new AccessForbiddenException("Students cannot download the student exams until " + EXAM_START_WAIT_TIME_MINUTES + " minutes before the exam start");
        }

        if (!user.getId().equals(studentExam.getUser().getId())) {
            throw new AccessForbiddenException("The requested exam does not belong to the requesting user");
        }

        if (!Boolean.TRUE.equals(studentExam.isStarted())) {
            messagingService.sendMessage("/topic/exam/" + examId + "/started", "");
        }

        // In case the studentExam is not yet started, a new participation wit a specific initialization date should be created - isStarted uses Boolean
        if (studentExam.getExam().isTestExam()) {
            prepareStudentExamForConductionWithInitializationDateSet(request, user, studentExam, (studentExam.isStarted() == null || !studentExam.isStarted()));
        }
        else {
            prepareStudentExamForConduction(request, user, studentExam);
        }

        log.info("getStudentExamForConduction done in {}ms for {} exercises for user {}", System.currentTimeMillis() - start, studentExam.getExercises().size(), user.getLogin());
        return ResponseEntity.ok(studentExam);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/test-run/{testRunId}/conduction : Find a specific test run for conduction.
     * This will be used for the actual conduction of the test run. The test run will be returned with the exercises
     * and with the student participation and with the submissions.
     * NOTE: when this is called it will also mark the test run as started
     *
     * @param courseId  the course to which the test run belongs to
     * @param examId    the exam to which the test run belongs to
     * @param request   the http request, used to extract headers
     * @param testRunId the id of the student exam of the test run
     * @return the ResponseEntity with status 200 (OK) and with the found test run as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/test-run/{testRunId}/conduction")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<StudentExam> getTestRunForConduction(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long testRunId, HttpServletRequest request) {
        // NOTE: it is important that this method has the same logic (except really small differences) as getStudentExamForConduction
        long start = System.currentTimeMillis();
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get the test run for exam {} with id {}", examId, testRunId);

        // 1st: load the testRun with all associated exercises
        StudentExam testRun = studentExamRepository.findWithExercisesById(testRunId).orElseThrow(() -> new EntityNotFoundException("StudentExam", testRunId));

        if (!currentUser.equals(testRun.getUser())) {
            throw new ConflictException("Current user is not the user of the test run", "StudentExam", "userMismatch");
        }

        studentExamAccessService.checkCourseAndExamAccessElseThrow(courseId, examId, currentUser, true, false);
        prepareStudentExamForConduction(request, currentUser, testRun);

        log.info("getTestRunForConduction done in {}ms for {} exercises for user {}", System.currentTimeMillis() - start, testRun.getExercises().size(), currentUser.getLogin());
        return ResponseEntity.ok(testRun);
    }

    @NotNull
    private StudentExam findStudentExamWithExercisesElseThrow(User user, Long examId, Long courseId) {
        StudentExam studentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(user.getId(), examId)
                .orElseThrow(() -> new EntityNotFoundException("No student exam found for examId " + examId + " and userId " + user.getId()));
        studentExamAccessService.checkCourseAndExamAccessElseThrow(courseId, examId, user, studentExam.isTestRun(), false);
        return studentExam;
    }

    /**
     * GET /courses/{courseId}/test-exams-per-user
     * Retrieves all StudentExams for test exams of one Course for the current user
     *
     * @param courseId the course to which the student exam belongs to
     * @return all StudentExams for test exam for the specified course and user
     */
    @GetMapping("courses/{courseId}/test-exams-per-user")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<StudentExam>> getStudentExamsForCoursePerUser(@PathVariable Long courseId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        studentExamAccessService.checkCourseAccessForStudentElseThrow(courseId, user);

        List<StudentExam> studentExamList = studentExamRepository.findStudentExamForTestExamsByUserIdAndCourseId(user.getId(), courseId);

        return ResponseEntity.ok(studentExamList);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/summary
     * Find a specified student exam for the summary of an exam. This will be used to display the summary of the exam.
     * The student exam will be returned with the exercises and with the student participation and with the submissions.
     *
     * @param courseId      the course to which the student exam belongs to
     * @param examId        the exam to which the student exam belongs to
     * @param studentExamId the studentExamId for which the summary should be loaded
     * @return the ResponseEntity with status 200 (OK) and with the found student exam as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/summary")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<StudentExam> getStudentExamForSummary(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long studentExamId) {
        long start = System.currentTimeMillis();
        User user = userRepository.getUserWithGroupsAndAuthorities();

        log.debug("REST request to get the student exam of user {} for exam {}", user.getLogin(), examId);

        // 1st: Get the studentExam from the database
        StudentExam studentExam = studentExamRepository.findByIdWithExercisesElseThrow(studentExamId);

        // 2nd: Check equal users and access permissions
        if (!user.equals(studentExam.getUser())) {
            throw new AccessForbiddenException("Current user is not the user of the requested student exam");
        }
        studentExamAccessService.checkCourseAndExamAccessElseThrow(courseId, examId, user, studentExam.isTestRun(), false);

        // 3rd: check that the studentExam has been submitted, otherwise /student-exams/{studentExamId}/conduction should be used
        if (!studentExam.isSubmitted()) {
            throw new AccessForbiddenException("You are not allowed to access the summary of a student exam which was NOT submitted!");
        }

        // 4th: Reload the Quiz-Exercises
        loadQuizExercisesForStudentExam(studentExam);

        // 5th fetch participations, submissions and results and connect them to the studentExam
        if (studentExam.getExam().isTestExam()) {
            fetchParticipationsSubmissionsAndResultsForTestExam(studentExam, user);
        }
        else {
            fetchParticipationsSubmissionsAndResultsForRealExam(studentExam, user);
        }

        log.info("getStudentExamForSummary done in {}ms for {} exercises for user {}", System.currentTimeMillis() - start, studentExam.getExercises().size(), user.getLogin());
        return ResponseEntity.ok(studentExam);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/student-exams/grade-summary : Return student exam result, aggregate points, assessment result
     * for a student exam and grade calculations if the exam is assessed. Only instructors can use userId parameter to get exam results of other users,
     * if the caller is a student, userId should either be the user id of the caller or empty.
     * <p>
     * Does not return the student exam itself to save bandwidth.
     * <p>
     * See {@link StudentExamWithGradeDTO} for more explanation.
     *
     * @param courseId  the course to which the student exam belongs to
     * @param examId    the exam to which the student exam belongs to
     * @param userId    the user id of the student whose grade summary is requested
     * @return the ResponseEntity with status 200 (OK) and with the StudentExamWithGradeDTO instance without the student exam as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/student-exams/grade-summary")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<StudentExamWithGradeDTO> getStudentExamGradesForSummary(@PathVariable Long courseId, @PathVariable Long examId,
            @RequestParam(required = false) Long userId) {
        long start = System.currentTimeMillis();
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get the student exam grades of user with id {} for exam {} by user {}", userId, examId, currentUser.getLogin());

        User targetUser = userId == null ? currentUser : userRepository.findByIdWithGroupsAndAuthoritiesElseThrow(userId);
        StudentExam studentExam = findStudentExamWithExercisesElseThrow(targetUser, examId, courseId);

        boolean isAtLeastInstructor = authorizationCheckService.isAtLeastInstructorInCourse(studentExam.getExam().getCourse(), currentUser);
        if (!isAtLeastInstructor && !currentUser.getId().equals(targetUser.getId())) {
            throw new AccessForbiddenException("Current user cannot access grade info for target user");
        }

        // check that the studentExam has been submitted, otherwise /student-exams/conduction should be used
        if (!studentExam.isSubmitted() || !studentExam.areResultsPublishedYet()) {
            throw new AccessForbiddenException("You are not allowed to access the grade summary of a student exam which was NOT submitted!");
        }

        loadQuizExercisesForStudentExam(studentExam);

        // 3rd fetch participations, submissions and results and connect them to the studentExam
        fetchParticipationsSubmissionsAndResultsForRealExam(studentExam, targetUser);

        List<StudentParticipation> participations = studentExam.getExercises().stream().flatMap(exercise -> exercise.getStudentParticipations().stream()).toList();
        // fetch all submitted answers for quizzes
        submittedAnswerRepository.loadQuizSubmissionsSubmittedAnswers(participations);

        StudentExamWithGradeDTO studentExamWithGradeDTO = examService.calculateStudentResultWithGradeAndPoints(studentExam, participations);

        log.info("getStudentExamGradesForSummary done in {}ms for {} exercises for target user {} by caller user {}", System.currentTimeMillis() - start,
                studentExam.getExercises().size(), targetUser.getLogin(), currentUser.getLogin());
        return ResponseEntity.ok(studentExamWithGradeDTO);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/test-runs : Find all test runs for the exam
     *
     * @param courseId the id of the course
     * @param examId   the id of the exam
     * @return the list of test runs
     */
    @GetMapping("courses/{courseId}/exams/{examId}/test-runs")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<StudentExam>> findAllTestRunsForExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to find all test runs for exam {}", examId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        List<StudentExam> testRuns = studentExamRepository.findAllTestRunsByExamId(examId);
        return ResponseEntity.ok(testRuns);
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/test-run : Create a test run
     *
     * @param courseId             the id of the course
     * @param examId               the id of the exam
     * @param testRunConfiguration the desired student exam configuration for the test run
     * @return the created test run student exam
     */
    @PostMapping("courses/{courseId}/exams/{examId}/test-run")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<StudentExam> createTestRun(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody StudentExam testRunConfiguration) {
        log.info("REST request to create a test run of exam {}", examId);
        if (testRunConfiguration.getExam() == null || !testRunConfiguration.getExam().getId().equals(examId)) {
            throw new BadRequestException();
        }
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        StudentExam testRun = studentExamService.createTestRun(testRunConfiguration);
        return ResponseEntity.ok(testRun);
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/student-exams/assess-unsubmitted-and-empty-student-exams : Assess unsubmitted student exams and empty submissions.
     * <p>
     * Finds student exams which the students did not submit on time i.e. {@link StudentExam#isSubmitted()} is false and assesses all exercises with 0 points in {@link StudentExamService#assessUnsubmittedStudentExams}.
     * Additionally assess all empty exercises with 0 points in {@link StudentExamService#assessEmptySubmissionsOfStudentExams}.
     * <p>
     * NOTE: A result with 0 points is only added if no other result is present for the latest submission of a relevant StudentParticipation.
     *
     * @param courseId the id of the course
     * @param examId   the id of the exam
     * @return {@link HttpStatus#BAD_REQUEST} if the exam is not over yet | {@link HttpStatus#FORBIDDEN} if the user is not an instructor
     */
    @PostMapping("/courses/{courseId}/exams/{examId}/student-exams/assess-unsubmitted-and-empty-student-exams")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> assessUnsubmittedStudentExamsAndEmptySubmissions(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to automatically assess the not submitted student exams of the exam with id {}", examId);

        final var exam = examRepository.findById(examId).orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, exam);

        if (!this.examDateService.isExamWithGracePeriodOver(exam)) {
            // you can only grade not submitted exams if the exam is over
            throw new BadRequestException();
        }

        // delete all test runs if the instructor forgot to delete them
        List<StudentExam> testRuns = studentExamRepository.findAllTestRunsByExamId(examId);
        testRuns.forEach(testRun -> studentExamService.deleteTestRun(testRun.getId()));

        final var instructor = userRepository.getUser();
        var assessedUnsubmittedStudentExams = studentExamService.assessUnsubmittedStudentExams(exam, instructor);
        log.info("Graded {} unsubmitted student exams of exam {}", assessedUnsubmittedStudentExams.size(), examId);

        studentExamService.assessEmptySubmissionsOfStudentExams(exam, instructor, assessedUnsubmittedStudentExams);

        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /courses/{courseId}/exams/{examId}/test-run/{testRunId} : Delete a test run
     *
     * @param courseId  the id of the course
     * @param examId    the id of the exam
     * @param testRunId the id of the student exam of the test run
     * @return the deleted test run student exam
     */
    @DeleteMapping("courses/{courseId}/exams/{examId}/test-run/{testRunId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<StudentExam> deleteTestRun(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long testRunId) {
        log.info("REST request to delete the test run with id {}", testRunId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        StudentExam testRun = studentExamService.deleteTestRun(testRunId);
        return ResponseEntity.ok(testRun);
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/student-exams/start-exercises : Generate the participation objects
     * for all the student exams belonging to the exam
     *
     * @param courseId the course to which the exam belongs to
     * @param examId   the exam to which the student exam belongs to
     * @return ResponseEntity containing the list of generated participations
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/student-exams/start-exercises")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> startExercises(@PathVariable Long courseId, @PathVariable Long examId) {
        long start = System.nanoTime();
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        final Exam exam = examRepository.findByIdWithRegisteredUsersExerciseGroupsAndExercisesElseThrow(examId);

        if (exam.isTestExam()) {
            throw new BadRequestAlertException("Start exercises is only allowed for real exams", "StudentExam", "startExerciseOnlyForRealExams");
        }

        examService.combineTemplateCommitsOfAllProgrammingExercisesInExam(exam);

        User instructor = userRepository.getUser();
        log.info("REST request to start exercises for student exams of exam {}", examId);
        AuditEvent auditEvent = new AuditEvent(instructor.getLogin(), Constants.PREPARE_EXERCISE_START, "examId=" + examId, "user=" + instructor.getLogin());
        auditEventRepository.add(auditEvent);

        studentExamService.startExercises(examId).thenAccept(numberOfGeneratedParticipations -> log.info("Generated {} participations in {} for student exams of exam {}",
                numberOfGeneratedParticipations, formatDurationFrom(start), examId));
        return ResponseEntity.ok().build();
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/student-exams/start-exercises/status : Return the current status of
     * starting exams for student exams in the given exam if available
     *
     * @param courseId the course to which the exam belongs to
     * @param examId   the exam to which the student exams belongs to
     * @return ResponseEntity containing the status
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/student-exams/start-exercises/status")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ExamExerciseStartPreparationStatus> getExerciseStartStatus(@PathVariable Long courseId, @PathVariable Long examId) {
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        return ResponseEntity.ok(studentExamService.getExerciseStartStatusOfExam(examId).orElse(null));
    }

    /**
     * Sets the started flag and initial started date.
     * Calls {@link StudentExamResource#fetchParticipationsSubmissionsAndResultsForRealExam} to set up the exercises.
     * Starts an exam session for the request
     * Filters out unnecessary attributes.
     *
     * @param request     the http request for the conduction
     * @param currentUser the current user
     * @param studentExam the student exam to be prepared
     */
    private void prepareStudentExamForConduction(HttpServletRequest request, User currentUser, StudentExam studentExam) {
        loadQuizExercisesForStudentExam(studentExam);

        // 2nd: mark the student exam as started
        studentExam.setStarted(true);
        if (studentExam.getStartedDate() == null) {
            studentExam.setStartedDate(ZonedDateTime.now());
        }
        studentExamRepository.save(studentExam);

        // 3rd fetch participations, submissions and results and connect them to the studentExam
        fetchParticipationsSubmissionsAndResultsForRealExam(studentExam, currentUser);
        for (var exercise : studentExam.getExercises()) {
            for (var participation : exercise.getStudentParticipations()) {
                // remove inner exercise from participation
                participation.setExercise(null);
            }
        }
        // 4th create new exam session
        createNewExamSession(request, studentExam);

        // not needed
        studentExam.getExam().setCourse(null);
    }

    /**
     * Prepares a Student Exam for conduction with the studentParticipations as argument, instead of fetching them from the database.
     *
     * @param request                the http request for the conduction
     * @param currentUser            the current user
     * @param studentExam            the student exam to be prepared
     * @param createNewParticipation if a new participation should be created or an existing participation should be fetched from the database
     */
    private void prepareStudentExamForConductionWithInitializationDateSet(HttpServletRequest request, User currentUser, StudentExam studentExam, boolean createNewParticipation) {

        if (createNewParticipation) {
            // 1st: Fix startedDate. As the studentExam.startedDate is used to link the participation.initializationDate, we need to drop the ms
            // (initializationDate is stored with ms)
            ZonedDateTime startedDate = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);

            // 2nd: Set up new participations for the Exercises and set initialisationDate to the startedDate
            studentExamService.setUpTestExamExerciseParticipationsAndSubmissions(studentExam, startedDate);

            // 3rd: mark the student exam as started
            studentExam.setStarted(true);
            studentExam.setStartedDate(startedDate);

        }
        // 4th: Fetch the relevant data from the database
        fetchParticipationsSubmissionsAndResultsForRealExam(studentExam, currentUser);

        // 5th: Reload the Quiz-Exercises
        loadQuizExercisesForStudentExam(studentExam);

        // 6th: Save StudentExam
        studentExamRepository.save(studentExam);

        // 7th create new exam session
        createNewExamSession(request, studentExam);

        // not needed
        studentExam.getExam().setCourse(null);
    }

    /**
     * Creates a new Exam Session
     *
     * @param request     the http request for the conduction
     * @param studentExam the student exam to be prepared
     */
    private void createNewExamSession(HttpServletRequest request, StudentExam studentExam) {
        final var ipAddress = !storeSessionDataInStudentExamSession ? null : HttpRequestUtils.getIpAddressFromRequest(request).orElse(null);
        final String browserFingerprint = !storeSessionDataInStudentExamSession ? null : request.getHeader("X-Artemis-Client-Fingerprint");
        final String instanceId = !storeSessionDataInStudentExamSession ? null : request.getHeader("X-Artemis-Client-Instance-ID");
        final String userAgent = !storeSessionDataInStudentExamSession ? null : request.getHeader("User-Agent");
        ExamSession examSession = this.examSessionService.startExamSession(studentExam, browserFingerprint, userAgent, instanceId, ipAddress);
        examSession.hideDetails();
        examSession.setInitialSession(this.examSessionService.checkExamSessionIsInitial(studentExam.getId()));
        studentExam.setExamSessions(Set.of(examSession));
    }

    /**
     * For all exercises from the student exam, fetch participation, submissions & result for the current user.
     *
     * @param studentExam the student exam in question
     * @param currentUser logged-in user with groups and authorities
     */
    private void fetchParticipationsSubmissionsAndResultsForRealExam(StudentExam studentExam, User currentUser) {
        // fetch participations, submissions and results for these exercises, note: exams only contain individual exercises for now
        // fetching all participations at once is more effective
        List<StudentParticipation> participations = studentParticipationRepository.findByStudentExamWithEagerSubmissionsResult(studentExam, false);
        // fetch all submitted answers for quizzes
        submittedAnswerRepository.loadQuizSubmissionsSubmittedAnswers(participations);

        boolean isAtLeastInstructor = authorizationCheckService.isAtLeastInstructorInCourse(studentExam.getExam().getCourse(), currentUser);

        // connect & filter the exercises and student participations including the latest submission and results where necessary, to make sure all relevant associations are
        // available
        for (Exercise exercise : studentExam.getExercises()) {
            filterParticipationForExercise(studentExam, exercise, participations, isAtLeastInstructor);
        }
    }

    /**
     * For all exercises from the test exam, fetch participation, submissions & result for the current user.
     * (Different way, as studentExam <-> participations are linked with the startedDate <-> initializationDate
     *
     * @param studentExam the student exam in question
     * @param user logged-in user with groups and authorities
     */
    private void fetchParticipationsSubmissionsAndResultsForTestExam(StudentExam studentExam, User user) {

        // 1st: fetch participations, submissions and results.
        List<StudentParticipation> participations = studentParticipationRepository
                .findParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissionsResultWithoutAssessor(studentExam);

        boolean isAtLeastInstructor = authorizationCheckService.isAtLeastInstructorInCourse(studentExam.getExam().getCourse(), user);

        // 2nd: connect & filter the exercises and student participations including the latest submission and results where necessary, to make sure all relevant associations are
        // available
        for (Exercise exercise : studentExam.getExercises()) {
            filterParticipationForExercise(studentExam, exercise, participations, isAtLeastInstructor);
        }
    }

    /**
     * Finds the participation in participations that belongs to the given exercise and filters all unnecessary and sensitive information.
     * This ensures all relevant associations are available.
     * Handles setting the participation results using {@link StudentExamResource#setResultIfNecessary(StudentExam, StudentParticipation, boolean)}.
     * Filters sensitive information using {@link Exercise#filterSensitiveInformation()} and {@link QuizSubmission#filterForExam(boolean, boolean)} for quiz exercises.
     *
     * @param studentExam         the given student exam
     * @param exercise            the exercise for which the user participation should be filtered
     * @param participations      the set of participations, wherein to search for the relevant participation
     * @param isAtLeastInstructor flag for instructor access privileges
     */
    private void filterParticipationForExercise(StudentExam studentExam, Exercise exercise, List<StudentParticipation> participations, boolean isAtLeastInstructor) {
        // remove the unnecessary inner course attribute
        exercise.setCourse(null);
        if (!(exercise instanceof QuizExercise)) {
            // Note: quiz exercises are filtered below
            exercise.filterSensitiveInformation();
        }

        if (!isAtLeastInstructor) {
            exercise.setExerciseGroup(null);
        }

        if (exercise instanceof ProgrammingExercise programmingExercise) {
            programmingExercise.setTestRepositoryUrl(null);
        }

        // get user's participation for the exercise
        StudentParticipation participation = participations != null ? exercise.findParticipation(participations) : null;

        // add relevant submission (relevancy depends on InitializationState) with its result to participation
        if (participation != null) {
            // only include the latest submission
            Optional<Submission> optionalLatestSubmission = participation.findLatestLegalOrIllegalSubmission();
            if (optionalLatestSubmission.isPresent()) {
                Submission latestSubmission = optionalLatestSubmission.get();
                latestSubmission.setParticipation(null);
                participation.setSubmissions(Set.of(latestSubmission));
                setResultIfNecessary(studentExam, participation, isAtLeastInstructor);

                if (exercise instanceof QuizExercise && latestSubmission instanceof QuizSubmission quizSubmission) {
                    // filter quiz solutions when the publishing result date is not set (or when set before the publish result date)
                    quizSubmission.filterForExam(studentExam.areResultsPublishedYet(), isAtLeastInstructor);
                }
            }
            else {
                // To prevent LazyInitializationException.
                participation.setResults(Set.of());
            }
            // add participation into an array
            exercise.setStudentParticipations(Set.of(participation));
        }
        else {
            // To prevent LazyInitializationException.
            exercise.setStudentParticipations(Set.of());
        }
    }

    /**
     * Helper method which attaches the result to its participation.
     * For direct automatic feedback during the exam conduction for {@link ProgrammingExercise}, we need to attach the results.
     * We also attach the result if the results are already published for the exam.
     * If no suitable Result is found for StudentParticipation, an empty Result set is assigned to prevent LazyInitializationException on future reads.
     * See {@link StudentExam#areResultsPublishedYet}
     * @param studentExam the given studentExam
     * @param participation the given participation of the student
     * @param isAtLeastInstructor flag for instructor access privileges
     */
    private void setResultIfNecessary(StudentExam studentExam, StudentParticipation participation, boolean isAtLeastInstructor) {
        // Only set the result during the exam for programming exercises (for direct automatic feedback) or after publishing the results
        boolean isStudentAllowedToSeeResult = (studentExam.getExam().isStarted() && !studentExam.isEnded() && participation instanceof ProgrammingExerciseStudentParticipation)
                || studentExam.areResultsPublishedYet();
        Optional<Submission> latestSubmission = participation.findLatestSubmission();

        // To prevent LazyInitializationException.
        participation.setResults(Set.of());
        if ((isStudentAllowedToSeeResult || isAtLeastInstructor) && latestSubmission.isPresent()) {
            var lastSubmission = latestSubmission.get();
            // Also set the latest result into the participation as the client expects it there for programming exercises
            Result latestResult = lastSubmission.getLatestResult();
            if (latestResult != null) {
                latestResult.setParticipation(null);
                latestResult.setSubmission(lastSubmission);
                // to avoid cycles and support certain use cases on the client, only the last result + submission inside the participation are relevant, i.e. participation ->
                // lastResult -> lastSubmission
                participation.setResults(Set.of(latestResult));
            }
            lastSubmission.setResults(null);
            participation.setSubmissions(Set.of(lastSubmission));
        }
    }

    /**
     * Loads the quiz questions as is not possible to load them in a generic way with the entity graph used.
     * See {@link StudentParticipationRepository#findByStudentExamWithEagerSubmissionsResult}
     *
     * @param studentExam the studentExam for which to load exercises
     */
    private void loadQuizExercisesForStudentExam(StudentExam studentExam) {
        for (int i = 0; i < studentExam.getExercises().size(); i++) {
            var exercise = studentExam.getExercises().get(i);
            if (exercise instanceof QuizExercise) {
                // reload and replace the quiz exercise
                var quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(exercise.getId());
                // filter quiz solutions when the publish result date is not set (or when set before the publish result date)
                if (!(studentExam.areResultsPublishedYet() || studentExam.isTestRun())) {
                    quizExercise.filterForStudentsDuringQuiz();
                }
                studentExam.getExercises().set(i, quizExercise);
            }
        }
    }

    /**
     * PUT /courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/toggle-to-submitted : Toggles the submission
     * status of the specified studentExam to submitted.
     *
     * @param courseId      the course to which the student exams belong to
     * @param examId        the exam to which the student exams belong to
     * @param studentExamId the student exam id where we want to set to be submitted
     * @return the student exam with the new state of submitted and submissionDate
     * 200 if successful
     */
    @PutMapping("/courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/toggle-to-submitted")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<StudentExam> submitStudentExam(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long studentExamId) {
        User instructor = userRepository.getUser();
        examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(courseId, examId, studentExamId);

        StudentExam studentExam = studentExamRepository.findById(studentExamId).orElseThrow(() -> new EntityNotFoundException("studentExam", studentExamId));
        if (studentExam.isSubmitted()) {
            throw new BadRequestException();
        }
        if (studentExam.getIndividualEndDateWithGracePeriod().isAfter(ZonedDateTime.now())) {
            throw new AccessForbiddenException("Exam", examId);
        }

        ZonedDateTime submissionTime = ZonedDateTime.now();
        studentExam.setSubmissionDate(submissionTime);
        studentExam.setSubmitted(true);

        log.info("REST request by user: {} for exam with id {} to set student-exam {} to SUBMITTED", instructor.getLogin(), examId, studentExamId);
        AuditEvent auditEvent = new AuditEvent(instructor.getLogin(), Constants.TOGGLE_STUDENT_EXAM_SUBMITTED, "examId=" + examId, "user=" + instructor.getLogin(),
                "studentExamId=" + studentExamId);
        auditEventRepository.add(auditEvent);

        return ResponseEntity.ok(studentExamRepository.save(studentExam));
    }

    /**
     * PUT /courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/toggle-to-submitted : Toggles the submission
     * status of the specified studentExam to unsubmitted.
     *
     * @param courseId      the course to which the student exams belong to
     * @param examId        the exam to which the student exams belong to
     * @param studentExamId the student exam id where we want to set it to be unsubmitted
     * @return the student exam with the new state of submitted and submissionDate
     * 200 if successful
     */
    @PutMapping("/courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/toggle-to-unsubmitted")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<StudentExam> unsubmitStudentExam(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long studentExamId) {
        User instructor = userRepository.getUser();

        examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(courseId, examId, studentExamId);

        StudentExam studentExam = studentExamRepository.findById(studentExamId).orElseThrow(() -> new EntityNotFoundException("studentExam", studentExamId));
        if (!studentExam.isSubmitted()) {
            throw new BadRequestException();
        }
        if (studentExam.getIndividualEndDateWithGracePeriod().isAfter(ZonedDateTime.now())) {
            throw new AccessForbiddenException("Exam", examId);
        }

        studentExam.setSubmissionDate(null);
        studentExam.setSubmitted(false);

        log.info("REST request by user: {} for exam with id {} to set student-exam {} to UNSUBMITTED", instructor.getLogin(), examId, studentExamId);
        AuditEvent auditEvent = new AuditEvent(instructor.getLogin(), Constants.TOGGLE_STUDENT_EXAM_UNSUBMITTED, "examId=" + examId, "user=" + instructor.getLogin(),
                "studentExamId=" + studentExamId);
        auditEventRepository.add(auditEvent);

        return ResponseEntity.ok(studentExamRepository.save(studentExam));
    }
}
