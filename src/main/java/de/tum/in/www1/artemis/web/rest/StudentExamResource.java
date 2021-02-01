package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExamSession;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.util.HttpRequestUtils;

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

    private final ExamService examService;

    private final ExamSessionService examSessionService;

    private final QuizExerciseService quizExerciseService;

    private final ParticipationService participationService;

    private final ExamRepository examRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public StudentExamResource(ExamAccessService examAccessService, StudentExamService studentExamService, StudentExamAccessService studentExamAccessService,
            UserService userService, StudentExamRepository studentExamRepository, ExamService examService, ExamSessionService examSessionService,
            ParticipationService participationService, QuizExerciseService quizExerciseService, ExamRepository examRepository,
            AuthorizationCheckService authorizationCheckService) {
        this.examAccessService = examAccessService;
        this.studentExamService = studentExamService;
        this.studentExamAccessService = studentExamAccessService;
        this.userService = userService;
        this.studentExamRepository = studentExamRepository;
        this.examService = examService;
        this.examSessionService = examSessionService;
        this.participationService = participationService;
        this.quizExerciseService = quizExerciseService;
        this.examRepository = examRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/student-exams/{studentExamId} : Find a student exam by id.
     *
     * @param courseId      the course to which the student exam belongs to
     * @param examId        the exam to which the student exam belongs to
     * @param studentExamId the id of the student exam to find
     * @return the ResponseEntity with status 200 (OK) and with the found student exam as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/student-exams/{studentExamId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<StudentExam> getStudentExam(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long studentExamId) {
        log.debug("REST request to get student exam : {}", studentExamId);
        Optional<ResponseEntity<StudentExam>> accessFailure = examAccessService.checkCourseAndExamAndStudentExamAccess(courseId, examId, studentExamId);
        if (accessFailure.isPresent()) {
            // the user must be instructor for the exam
            return accessFailure.get();
        }

        StudentExam studentExam = studentExamService.findOneWithExercises(studentExamId);

        loadExercisesForStudentExam(studentExam);

        // fetch participations, submissions and results for these exercises, note: exams only contain individual exercises for now
        // fetching all participations at once is more effective
        List<StudentParticipation> participations = participationService.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(studentExam.getUser().getId(),
                studentExam.getExercises());

        // connect the exercises and student participations correctly and make sure all relevant associations are available
        for (Exercise exercise : studentExam.getExercises()) {
            // add participation with submission and result to each exercise
            filterParticipation(studentExam, exercise, participations, true);
        }

        return ResponseEntity.ok(studentExam);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/studentExams : Get all student exams for the given exam
     *
     * @param courseId the course to which the student exams belong to
     * @param examId   the exam to which the student exams belong to
     * @return the ResponseEntity with status 200 (OK) and a set of student exams. The set can be empty
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/student-exams")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Set<StudentExam>> getStudentExamsForExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get all student exams for exam : {}", examId);
        Optional<ResponseEntity<Set<StudentExam>>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        return courseAndExamAccessFailure.orElseGet(() -> ResponseEntity.ok(studentExamService.findAllByExamId(examId)));
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
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<StudentExam> updateWorkingTime(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long studentExamId,
            @RequestBody Integer workingTime) {
        log.debug("REST request to update the working time of student exam : {}", studentExamId);
        Optional<ResponseEntity<StudentExam>> accessFailure = examAccessService.checkCourseAndExamAndStudentExamAccess(courseId, examId, studentExamId);
        if (accessFailure.isPresent()) {
            return accessFailure.get();
        }
        if (workingTime <= 0) {
            return badRequest();
        }
        StudentExam studentExam = studentExamService.findOneWithExercises(studentExamId);
        if (!studentExam.isTestRun()) {
            Exam exam = examRepository.findById(examId).get();
            // when the exam is already visible, the working time cannot be changed, due to permission issues with unlock and lock operations for programming exercises
            if (ZonedDateTime.now().isAfter(exam.getVisibleDate())) {
                return badRequest();
            }
        }

        studentExam.setWorkingTime(workingTime);
        return ResponseEntity.ok(studentExamRepository.save(studentExam));
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
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StudentExam> submitStudentExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody StudentExam studentExam) {
        log.debug("REST request to mark the studentExam as submitted : {}", studentExam.getId());
        User currentUser = userService.getUserWithGroupsAndAuthorities();
        boolean isTestRun = studentExam.isTestRun();
        Optional<ResponseEntity<StudentExam>> accessFailure = this.studentExamAccessService.checkStudentExamAccess(courseId, examId, studentExam.getId(), currentUser, isTestRun);
        if (accessFailure.isPresent()) {
            return accessFailure.get();
        }

        StudentExam existingStudentExam = studentExamService.findOneWithExercises(studentExam.getId());
        if (Boolean.TRUE.equals(studentExam.isSubmitted()) || Boolean.TRUE.equals(existingStudentExam.isSubmitted())) {
            log.error("Student exam with id {} for user {} is already submitted.", studentExam.getId(), currentUser.getLogin());
            return conflict("studentExam", "alreadySubmitted", "You have already submitted.");
        }

        // checks if student exam is live (after start date, before end date + grace period)
        if (!isTestRun && (existingStudentExam.getExam().getStartDate() != null && !ZonedDateTime.now().isAfter(existingStudentExam.getExam().getStartDate())
                || existingStudentExam.getIndividualEndDate() != null && !ZonedDateTime.now().isBefore(existingStudentExam.getIndividualEndDateWithGracePeriod()))) {
            return forbidden("studentExam", "submissionNotInTime", "You can only submit between start and end of the exam.");
        }

        return studentExamService.submitStudentExam(existingStudentExam, studentExam, currentUser);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/student-exams/conduction : Find a student exam for the user.
     * This will be used for the actual conduction of the exam. The student exam will be returned with the exercises
     * and with the student participation and with the submissions.
     * NOTE: when this is called it will also mark the student exam as started
     *
     * @param courseId the course to which the student exam belongs to
     * @param examId   the exam to which the student exam belongs to
     * @param request  the http request, used to extract headers
     * @return the ResponseEntity with status 200 (OK) and with the found student exam as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/student-exams/conduction")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StudentExam> getStudentExamForConduction(@PathVariable Long courseId, @PathVariable Long examId, HttpServletRequest request) {
        // NOTE: it is important that this method has the same logic (except really small differences) as getTestRunForConduction
        long start = System.currentTimeMillis();
        User user = userService.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get the student exam of user {} for exam {}", user.getLogin(), examId);

        // 1st: load the studentExam with all associated exercises
        Optional<StudentExam> optionalStudentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(user.getId(), examId);
        if (optionalStudentExam.isEmpty()) {
            return notFound();
        }
        var studentExam = optionalStudentExam.get();

        Optional<ResponseEntity<StudentExam>> courseAndExamAccessFailure = studentExamAccessService.checkCourseAndExamAccess(courseId, examId, user, studentExam.isTestRun());
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        prepareStudentExamForConduction(request, user, studentExam);

        log.info("getStudentExamForConduction done in " + (System.currentTimeMillis() - start) + "ms for " + studentExam.getExercises().size() + " exercises for user "
                + user.getLogin());
        return ResponseEntity.ok(studentExam);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/test-run/{testRunId}/conduction : Find a specific test run for conduction.
     * This will be used for the actual conduction of the test run. The test run will be returned with the exercises
     * and with the student participation and with the submissions.
     * NOTE: when this is called it will also mark the test run as started
     *
     * @param courseId the course to which the test run belongs to
     * @param examId   the exam to which the test run belongs to
     * @param request  the http request, used to extract headers
     * @param testRunId the id of the student exam of the test run
     * @return the ResponseEntity with status 200 (OK) and with the found test run as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/test-run/{testRunId}/conduction")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StudentExam> getTestRunForConduction(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long testRunId, HttpServletRequest request) {
        // NOTE: it is important that this method has the same logic (except really small differences) as getStudentExamForConduction
        long start = System.currentTimeMillis();
        User currentUser = userService.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get the test run for exam {} with id {}", examId, testRunId);

        // 1st: load the testRun with all associated exercises
        Optional<StudentExam> optionalTestRun = studentExamRepository.findWithExercisesById(testRunId);
        if (optionalTestRun.isEmpty()) {
            return notFound();
        }
        var testRun = optionalTestRun.get();

        if (!currentUser.equals(testRun.getUser())) {
            return conflict();
        }

        Optional<ResponseEntity<StudentExam>> courseAndExamAccessFailure = studentExamAccessService.checkCourseAndExamAccess(courseId, examId, currentUser, true);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        prepareStudentExamForConduction(request, currentUser, testRun);

        log.info("getTestRunForConduction done in " + (System.currentTimeMillis() - start) + "ms for " + testRun.getExercises().size() + " exercises for user "
                + currentUser.getLogin());
        return ResponseEntity.ok(testRun);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/student-exams/summary : Find a student exam for the summary.
     * This will be used to display the summary of the exam. The student exam will be returned with the exercises
     * and with the student participation and with the submissions.
     *
     * @param courseId  the course to which the student exam belongs to
     * @param examId    the exam to which the student exam belongs to
     * @return the ResponseEntity with status 200 (OK) and with the found student exam as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/student-exams/summary")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StudentExam> getStudentExamForSummary(@PathVariable Long courseId, @PathVariable Long examId) {
        long start = System.currentTimeMillis();
        User user = userService.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get the student exam of user {} for exam {}", user.getLogin(), examId);

        // 1st: load the studentExam with all associated exercises
        Optional<StudentExam> optionalStudentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(user.getId(), examId);
        if (optionalStudentExam.isEmpty()) {
            return notFound();
        }
        var studentExam = optionalStudentExam.get();

        Optional<ResponseEntity<StudentExam>> courseAndExamAccessFailure = studentExamAccessService.checkCourseAndExamAccess(courseId, examId, user, studentExam.isTestRun());
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        // check that the studentExam has been submitted, otherwise /student-exams/conduction should be used
        if (!studentExam.isSubmitted()) {
            return forbidden();
        }

        loadExercisesForStudentExam(studentExam);

        // 3rd fetch participations, submissions and results and connect them to the studentExam
        fetchParticipationsSubmissionsAndResultsForStudentExam(studentExam, user, false);

        // not needed
        studentExam.getExam().setCourse(null);

        log.info("getStudentExamForSummary done in " + (System.currentTimeMillis() - start) + "ms for " + studentExam.getExercises().size() + " exercises for user "
                + user.getLogin());
        return ResponseEntity.ok(studentExam);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/test-runs : Find all test runs for the exam
     * @param courseId the id of the course
     * @param examId the id of the exam
     * @return the list of test runs
     */
    @GetMapping("courses/{courseId}/exams/{examId}/test-runs")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<StudentExam>> findAllTestRunsForExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to find all test runs for exam {}", examId);

        Optional<ResponseEntity<List<StudentExam>>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        List<StudentExam> testRuns = studentExamService.findAllTestRuns(examId);
        return ResponseEntity.ok(testRuns);
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/test-run : Create a test run
     * @param courseId the id of the course
     * @param examId the id of the exam
     * @param testRunConfiguration the desired student exam configuration for the test run
     * @return the created test run student exam
     */
    @PostMapping("courses/{courseId}/exams/{examId}/test-run")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StudentExam> createTestRun(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody StudentExam testRunConfiguration) {
        log.info("REST request to create a test run of exam {}", examId);

        if (testRunConfiguration.getExam() == null || !testRunConfiguration.getExam().getId().equals(examId)) {
            return badRequest();
        }

        Optional<ResponseEntity<StudentExam>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        StudentExam testRun = studentExamService.createTestRun(testRunConfiguration);
        return ResponseEntity.ok(testRun);
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/student-exams/assess-unsubmitted-and-empty-student-exams : Assess unsubmitted student exams and empty submissions.
     *
     * Finds student exams which the students did not submit on time i.e {@link StudentExam#isSubmitted()} is false and assesses all modeling- and text exercises with 0 points in {@link StudentExamService#assessUnsubmittedStudentExams}.
     * Additionally assess all empty modeling and text exercises with 0 points in {@link StudentExamService#assessEmptySubmissionsOfStudentExams}.
     *
     * NOTE: A result with 0 points is only added if no other result is present for the latest submission of a relevant StudentParticipation.
     *
     * @param courseId the id of the course
     * @param examId the id of the exam
     * @return {@link HttpStatus#BAD_REQUEST} if the exam is not over yet | {@link HttpStatus#FORBIDDEN} if the user is not an instructor
     */
    @PostMapping("/courses/{courseId}/exams/{examId}/student-exams/assess-unsubmitted-and-empty-student-exams")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> assessUnsubmittedStudentExamsAndEmptySubmissions(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to automatically assess the not submitted student exams of the exam with id {}", examId);

        final var exam = examService.findOne(examId);

        Optional<ResponseEntity<Void>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, exam);
        if (courseAndExamAccessFailure.isPresent()) {
            return forbidden();
        }

        if (!this.examService.isExamOver(exam)) {
            // you can only grade not submitted exams if the exam is over
            return badRequest();
        }

        // delete all test runs if the instructor forgot to delete them
        List<StudentExam> testRuns = studentExamService.findAllTestRuns(examId);
        for (final var testRun : testRuns) {
            studentExamService.deleteTestRun(testRun.getId());
        }

        final var instructor = userService.getUser();
        var assessedUnsubmittedStudentExams = studentExamService.assessUnsubmittedStudentExams(exam, instructor);
        log.info("Graded {} unsubmitted student exams of exam {}", assessedUnsubmittedStudentExams.size(), examId);

        studentExamService.assessEmptySubmissionsOfStudentExams(exam, instructor, assessedUnsubmittedStudentExams);

        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /courses/{courseId}/exams/{examId}/test-run/{testRunId} : Delete a test run
     * @param courseId the id of the course
     * @param examId the id of the exam
     * @param testRunId the id of the student exam of the test run
     * @return the deleted test run student exam
     */
    @DeleteMapping("courses/{courseId}/exams/{examId}/test-run/{testRunId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StudentExam> deleteTestRun(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long testRunId) {
        log.info("REST request to delete the test run with id {}", testRunId);

        Optional<ResponseEntity<StudentExam>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccessForInstructor(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        StudentExam testRun = studentExamService.deleteTestRun(testRunId);
        return ResponseEntity.ok(testRun);
    }

    /**
     * Sets the started flag and initial started date.
     * Calls {@link StudentExamResource#fetchParticipationsSubmissionsAndResultsForStudentExam} to set up the exercises.
     * Starts an exam session for the request
     * Filters out unnecessary attributes.
     * @param request the http request for the conduction
     * @param currentUser the current user
     * @param studentExam the student exam to be prepared
     */
    private void prepareStudentExamForConduction(HttpServletRequest request, User currentUser, StudentExam studentExam) {
        loadExercisesForStudentExam(studentExam);

        // 2nd: mark the student exam as started
        studentExam.setStarted(true);
        if (studentExam.getStartedDate() == null) {
            studentExam.setStartedDate(ZonedDateTime.now());
        }
        studentExamRepository.save(studentExam);

        // 3rd fetch participations, submissions and results and connect them to the studentExam
        fetchParticipationsSubmissionsAndResultsForStudentExam(studentExam, currentUser, true);

        // 4th create new exam session
        final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request).orElse(null);
        final String browserFingerprint = request.getHeader("X-Artemis-Client-Fingerprint");
        final String userAgent = request.getHeader("User-Agent");
        final String instanceId = request.getHeader("X-Artemis-Client-Instance-ID");
        ExamSession examSession = this.examSessionService.startExamSession(studentExam, browserFingerprint, userAgent, instanceId, ipAddress);
        examSession.hideDetails();
        studentExam.setExamSessions(Set.of(examSession));

        // not needed
        studentExam.getExam().setCourse(null);
    }

    /**
     * For all exercises from the student exam, fetch participation, submissions & result for the current user.
     *
     * @param studentExam the student exam in question
     * @param currentUser logged in user with groups and authorities
     */
    private void fetchParticipationsSubmissionsAndResultsForStudentExam(StudentExam studentExam, User currentUser, boolean forConduction) {
        // fetch participations, submissions and results for these exercises, note: exams only contain individual exercises for now
        // fetching all participations at once is more effective
        List<StudentParticipation> participations = participationService.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(currentUser.getId(),
                studentExam.getExercises());

        boolean isAtLeastInstructor = authorizationCheckService.isAtLeastInstructorInCourse(studentExam.getExam().getCourse(), currentUser);

        // connect & filter the exercises and student participations including the latest submission and results where necessary, to make sure all relevant associations are
        // available
        for (Exercise exercise : studentExam.getExercises()) {
            filterParticipation(studentExam, exercise, participations, isAtLeastInstructor);
        }
    }

    /**
     * Finds the participation in participations that belongs to the given exercise and filters all unnecessary and sensitive information.
     * This ensures all relevant associations are available.
     * Handles setting the participation results using {@link StudentExamResource#setResultIfNecessary(StudentExam, StudentParticipation, boolean)}.
     * Filters sensitive information using {@link Exercise#filterSensitiveInformation()} and {@link QuizSubmission#filterForExam(boolean, boolean)} for quiz exercises.
     * @param studentExam the given student exam
     * @param exercise the exercise for which the user participation should be filtered
     * @param participations the set of participations, wherein to search for the relevant participation
     * @param isAtLeastInstructor flag for instructor access privileges
     */
    private void filterParticipation(StudentExam studentExam, Exercise exercise, List<StudentParticipation> participations, boolean isAtLeastInstructor) {
        // remove the unnecessary inner course attribute
        exercise.setCourse(null);
        exercise.setExerciseGroup(null);

        if (exercise instanceof ProgrammingExercise) {
            ((ProgrammingExercise) exercise).setTestRepositoryUrl(null);
        }

        // get user's participation for the exercise
        StudentParticipation participation = participations != null ? exercise.findRelevantParticipation(participations) : null;

        // add relevant submission (relevancy depends on InitializationState) with its result to participation
        if (participation != null) {
            // remove inner exercise from participation
            participation.setExercise(null);
            // only include the latest submission
            Optional<Submission> optionalLatestSubmission = participation.findLatestSubmission();
            if (optionalLatestSubmission.isPresent()) {
                Submission latestSubmission = optionalLatestSubmission.get();
                latestSubmission.setParticipation(null);
                participation.setSubmissions(Set.of(latestSubmission));
                setResultIfNecessary(studentExam, participation, isAtLeastInstructor);

                if (exercise instanceof QuizExercise) {
                    // filter quiz solutions when the publish result date is not set (or when set before the publish result date)
                    ((QuizSubmission) latestSubmission).filterForExam(studentExam.areResultsPublishedYet(), isAtLeastInstructor);
                }
                else {
                    // Note: sensitive information for quizzes was already removed above
                    exercise.filterSensitiveInformation();
                }
            }
            // add participation into an array
            exercise.setStudentParticipations(Set.of(participation));
        }
    }

    /**
     * Helper method which attaches the result to its participation.
     * For direct automatic feedback during the exam conduction for {@link ProgrammingExercise}, we need to attach the results.
     * We also attach the result if the results are already published for the exam. See {@link StudentExam#areResultsPublishedYet}
     * @param studentExam the given studentExam
     * @param participation the given participation of the student
     * @param isAtLeastInstructor flag for instructor access privileges
     */
    private void setResultIfNecessary(StudentExam studentExam, StudentParticipation participation, boolean isAtLeastInstructor) {
        // Only set the result during the exam for programming exercises (for direct automatic feedback) or after publishing the results
        boolean isStudentAllowedToSeeResult = (studentExam.getExam().isStarted() && !studentExam.isEnded() && participation instanceof ProgrammingExerciseStudentParticipation)
                || studentExam.areResultsPublishedYet();
        Optional<Submission> latestSubmission = participation.findLatestSubmission();

        if ((isStudentAllowedToSeeResult || isAtLeastInstructor) && latestSubmission.isPresent()) {
            var submission = latestSubmission.get();
            // Also set the latest result into the participation as the client expects it there for programming exercises
            Result result = submission.getLatestResult();
            if (result != null) {
                result.setParticipation(null);
                result.setSubmission(null);
                participation.setResults(Set.of(result));
                submission.setResults(List.of(result));
            }
        }
    }

    /**
     * Loads the quiz questions as is not possible to load them in a generic way with the entity graph used.
     * See {@link ParticipationService#findByStudentIdAndIndividualExercisesWithEagerSubmissionsResult}
     *
     * @param studentExam the studentExam for which to load exercises
     */
    private void loadExercisesForStudentExam(StudentExam studentExam) {
        for (int i = 0; i < studentExam.getExercises().size(); i++) {
            var exercise = studentExam.getExercises().get(i);
            if (exercise instanceof QuizExercise) {
                // reload and replace the quiz exercise
                var quizExercise = quizExerciseService.findOneWithQuestions(exercise.getId());
                // filter quiz solutions when the publish result date is not set (or when set before the publish result date)
                if (!(studentExam.areResultsPublishedYet() || studentExam.isTestRun())) {
                    quizExercise.filterForStudentsDuringQuiz();
                }
                studentExam.getExercises().set(i, quizExercise);
            }
        }
    }

}
