package de.tum.cit.aet.artemis.exam.web;

import static de.tum.cit.aet.artemis.core.util.TimeLogUtil.formatDurationFrom;
import static java.time.ZonedDateTime.now;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.core.util.ExamExerciseStartPreparationStatus;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.course.repository.CourseAthenaConfigRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.domain.event.ExamLiveEvent;
import de.tum.cit.aet.artemis.exam.dto.AthenaFeedbackUsageDTO;
import de.tum.cit.aet.artemis.exam.dto.CreateTestRunDTO;
import de.tum.cit.aet.artemis.exam.dto.StudentExamDTO;
import de.tum.cit.aet.artemis.exam.dto.StudentExamWithGradeDTO;
import de.tum.cit.aet.artemis.exam.dto.examevent.ExamAttendanceCheckEventDTO;
import de.tum.cit.aet.artemis.exam.dto.examevent.ExamLiveEventBaseDTO;
import de.tum.cit.aet.artemis.exam.dto.submit.SubmitStudentExamDTO;
import de.tum.cit.aet.artemis.exam.dto.summary.StudentExamForSummaryDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamLiveEventRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exam.service.ExamAccessService;
import de.tum.cit.aet.artemis.exam.service.ExamDateService;
import de.tum.cit.aet.artemis.exam.service.ExamDeletionService;
import de.tum.cit.aet.artemis.exam.service.ExamReadAudience;
import de.tum.cit.aet.artemis.exam.service.ExamService;
import de.tum.cit.aet.artemis.exam.service.StudentExamAccessService;
import de.tum.cit.aet.artemis.exam.service.StudentExamAthenaFeedbackService;
import de.tum.cit.aet.artemis.exam.service.StudentExamLiveEventService;
import de.tum.cit.aet.artemis.exam.service.StudentExamService;

/**
 * REST controller for student exam management and owner summaries.
 */
@Conditional(ExamEnabled.class)
@Lazy
@FeatureUsage(UserFeature.EXAM_CONDUCTION_MANAGEMENT)
@RestController
@RequestMapping("api/exam/")
public class StudentExamResource {

    private static final Logger log = LoggerFactory.getLogger(StudentExamResource.class);

    private final ExamAccessService examAccessService;

    private final ExamDeletionService examDeletionService;

    private final StudentExamService studentExamService;

    private final StudentExamAthenaFeedbackService studentExamAthenaFeedbackService;

    private final StudentExamAccessService studentExamAccessService;

    private final UserRepository userRepository;

    private final AuditEventRepository auditEventRepository;

    private final StudentExamRepository studentExamRepository;

    private final ExamDateService examDateService;

    private final ExamRepository examRepository;

    private final ExamService examService;

    private final WebsocketMessagingService websocketMessagingService;

    private final ExamLiveEventRepository examLiveEventRepository;

    private final StudentExamLiveEventService studentExamLiveEventService;

    private static final String ENTITY_NAME = "studentExam";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final CourseAthenaConfigRepository courseAthenaConfigRepository;

    public StudentExamResource(ExamAccessService examAccessService, ExamDeletionService examDeletionService, StudentExamService studentExamService,
            StudentExamAthenaFeedbackService studentExamAthenaFeedbackService, StudentExamAccessService studentExamAccessService, UserRepository userRepository,
            AuditEventRepository auditEventRepository, StudentExamRepository studentExamRepository, ExamDateService examDateService, ExamRepository examRepository,
            ExamService examService, WebsocketMessagingService websocketMessagingService, ExamLiveEventRepository examLiveEventRepository,
            StudentExamLiveEventService studentExamLiveEventService, CourseAthenaConfigRepository courseAthenaConfigRepository) {
        this.courseAthenaConfigRepository = courseAthenaConfigRepository;
        this.examAccessService = examAccessService;
        this.examDeletionService = examDeletionService;
        this.studentExamService = studentExamService;
        this.studentExamAthenaFeedbackService = studentExamAthenaFeedbackService;
        this.studentExamAccessService = studentExamAccessService;
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;
        this.studentExamRepository = studentExamRepository;
        this.examDateService = examDateService;
        this.examRepository = examRepository;
        this.examService = examService;
        this.websocketMessagingService = websocketMessagingService;
        this.examLiveEventRepository = examLiveEventRepository;
        this.studentExamLiveEventService = studentExamLiveEventService;
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
    @GetMapping("courses/{courseId}/exams/{examId}/student-exams/{studentExamId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<StudentExamWithGradeDTO> getStudentExam(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long studentExamId) {
        log.debug("REST request to get student exam : {}", studentExamId);
        examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(courseId, examId, studentExamId);
        StudentExamWithGradeDTO studentExamWithGradeDTO = studentExamService.getStudentExamWithGradeDTO(examId, studentExamId);
        return ResponseEntity.ok(studentExamWithGradeDTO);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/student-exams : Get all student exams for the given exam
     *
     * @param courseId the course to which the student exams belong to
     * @param examId   the exam to which the student exams belong to
     * @return the ResponseEntity with status 200 (OK) and a list of student exams (without the nested exam). The list can be empty
     */
    @GetMapping("courses/{courseId}/exams/{examId}/student-exams")
    @EnforceAtLeastStudent
    @ManualConfig
    public ResponseEntity<List<StudentExamDTO>> getStudentExamsForExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get all student exams for exam : {}", examId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        var studentExams = studentExamRepository.findByExamIdWithSessions(examId);
        // reduce payload: the nested exam is intentionally omitted (see StudentExamDTO#of)
        List<StudentExamDTO> studentExamDTOs = studentExams.stream().map(StudentExamDTO::of).toList();
        return ResponseEntity.ok(studentExamDTOs);
    }

    /**
     * PATCH /courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/working-time : Update the working time of the student exam
     *
     * @param courseId      the course to which the student exams belong to
     * @param examId        the exam to which the student exams belong to
     * @param studentExamId the id of the student exam to find
     * @param workingTime   the new working time in seconds
     * @return the ResponseEntity with status 200 (OK) and with the updated student exam (including its nested exam and course) as body
     */
    @PatchMapping("courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/working-time")
    @EnforceAtLeastStudent
    @ManualConfig
    public ResponseEntity<StudentExamDTO> updateWorkingTime(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long studentExamId,
            @RequestBody Integer workingTime) {
        log.debug("REST request to update the working time of student exam : {}", studentExamId);

        examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(courseId, examId, studentExamId);

        StudentExam studentExam = studentExamLiveEventService.updateWorkingTime(examId, studentExamId, workingTime);
        return ResponseEntity.ok(StudentExamDTO.withExam(studentExam));
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/students/{studentLogin}/attendance-check : Throw attendance Check Event in the student exam
     *
     * @param courseId     the course to which the student exams belong to
     * @param examId       the exam to which the student exams belong to
     * @param studentLogin the id of the student exam to find
     * @param message      the optional message to be sent to the student
     * @return the ResponseEntity with status 200 (OK) and with the updated student exam as body
     */
    @FeatureUsage(UserFeature.EXAM_ATTENDANCE)
    @PostMapping("courses/{courseId}/exams/{examId}/students/{studentLogin:" + Constants.LOGIN_REGEX + "}/attendance-check")
    @EnforceAtLeastStudent
    @ManualConfig
    public ResponseEntity<ExamAttendanceCheckEventDTO> attendanceCheck(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable String studentLogin,
            @RequestBody(required = false) String message) {
        log.debug("REST request for attendance-check for student : {}", studentLogin);

        examAccessService.checkCourseAndExamAccessForTeachingAssistantElseThrow(courseId, examId);

        return ResponseEntity.ok(studentExamLiveEventService.createAttendanceCheckEvent(examId, studentLogin, message));
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/student-exams/submit : Submits the student exam
     * Updates all submissions and marks student exam as submitted. Only the student exam id and the last-second
     * submission changes are read from the body; ownership, exam/course validation and the test-run/test-exam gating
     * are derived from the authoritative student exam loaded from the database.
     *
     * @param courseId              the course to which the student exams belong to
     * @param examId                the exam to which the student exams belong to
     * @param studentExamFromClient the student exam id with the exercises, participations and submissions carrying the last-second changes
     * @return empty response with status code:
     *         200 if successful
     *         400 if student exam was in an illegal state
     */
    @FeatureUsage(UserFeature.EXAM_TAKE)
    @PostMapping("courses/{courseId}/exams/{examId}/student-exams/submit")
    @EnforceAtLeastStudent
    // NOTE: the body is intentionally NOT @Valid — the legacy full-entity endpoint activated no Bean Validation, and the
    // DTO migration must not add validation the legacy endpoint lacked (a rejected body would break the hand-in instead
    // of degrading gracefully). Malformed/ambiguous parts are tolerated and dropped during reconstruction.
    public ResponseEntity<Void> submitStudentExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody SubmitStudentExamDTO studentExamFromClient) {
        long start = System.nanoTime();
        log.debug("REST request to mark the studentExam as submitted : {}", studentExamFromClient.id());

        // The authenticated login comes from the security context and costs no query.
        String currentUserLogin = userRepository.getCurrentUserLogin();

        // 1. DB Call: read the authoritative student exam; all downstream truth (ownership, exam/course, submitted
        // flag, test-run/test-exam gating) comes from this DB entity, not from the client body.
        StudentExam existingStudentExam = studentExamRepository.findByIdWithExercisesElseThrow(studentExamFromClient.id());
        // Ownership is checked against the persisted owner instead of a client-supplied user field. This is the
        // behavior-equivalent replacement for the previous anti-manipulation gate and closes the latent hole where the
        // client-claimed user was the only ownership check.
        // StudentExam#user is a @ManyToOne and therefore already loaded here, so once this comparison holds it IS the
        // current user: loading the same row a second time through the user repository would add nothing.
        User currentUser = existingStudentExam.getUser();
        if (!Objects.equals(currentUser.getLogin(), currentUserLogin)) {
            throw new AccessForbiddenException("Current user is not the user of the requested student exam");
        }
        studentExamAccessService.validateExamRequestParametersElseThrow(existingStudentExam, examId, courseId);

        if (Boolean.TRUE.equals(existingStudentExam.isSubmitted())) {
            log.error("Student exam with id {} for user {} is already submitted.", studentExamFromClient.id(), currentUser.getLogin());
            // NOTE: we should not send an error message to the user here, due to overload it could happen that the call is sent multiple times
            return ResponseEntity.ok().build();
        }

        // checks if student exam is live (after start date, before end date + grace period)
        if (!existingStudentExam.isTestRun() && (existingStudentExam.getExam().getStartDate() != null && !now().isAfter(existingStudentExam.getExam().getStartDate())
                || existingStudentExam.getIndividualEndDate() != null && !now().isBefore(existingStudentExam.getIndividualEndDateWithGracePeriod()))) {
            throw new AccessForbiddenException("You can only submit between start and end of the exam.");
        }

        log.debug("Completed input validation for submitStudentExam in {}", formatDurationFrom(start));

        // The service reconstructs the transient graph from the slim DTO and then runs the (unchanged) submit machinery.
        studentExamService.submitStudentExam(existingStudentExam, studentExamFromClient, currentUser);

        websocketMessagingService.sendMessage(ExamWebsocketTopics.EXAM_SUBMITTED.at(examId), "");

        log.info("Completed submitStudentExam with {} exercises for user {} in a total time of {}", existingStudentExam.getExercises().size(), currentUser.getLogin(),
                formatDurationFrom(start));
        return ResponseEntity.ok().build();
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/request-feedback : Request Athena AI
     * feedback for all text and modeling exercises in the given submitted test exam or instructor test run.
     *
     * @param courseId      the course to which the exam belongs
     * @param examId        the exam to which the student exam belongs
     * @param studentExamId the id of the student exam
     * @return 200 OK if the feedback request was accepted
     */
    @FeatureUsage(UserFeature.AI_FEEDBACK_REQUEST)
    @PostMapping("courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/request-feedback")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> requestAthenaFeedback(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long studentExamId) {
        log.debug("REST request to trigger Athena feedback for student exam {}", studentExamId);
        User currentUser = userRepository.getUser();
        StudentExam studentExam = studentExamRepository.findByIdWithExercisesElseThrow(studentExamId);
        studentExamAccessService.validateExamRequestParametersElseThrow(studentExam, examId, courseId);
        if (!Objects.equals(currentUser.getId(), studentExam.getUser().getId())) {
            throw new AccessForbiddenException("Current user is not the user of the requested student exam");
        }
        checkCourseAccessForTestRunElseThrow(studentExam, examId, courseId, currentUser);
        studentExamAthenaFeedbackService.requestAthenaFeedback(studentExam, currentUser);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/athena-feedback-usage : Return how many
     * Athena AI feedback requests the current user has already used for this test exam (or test run) and the
     * configured cap.
     *
     * @param courseId      the course to which the exam belongs
     * @param examId        the exam to which the student exam belongs
     * @param studentExamId the id of the student exam
     * @return 200 OK with the usage information
     */
    @FeatureUsage(UserFeature.AI_FEEDBACK_REQUEST)
    @GetMapping("courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/athena-feedback-usage")
    @EnforceAtLeastStudent
    public ResponseEntity<AthenaFeedbackUsageDTO> getAthenaFeedbackUsage(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long studentExamId) {
        // The user is needed for the test-run course access check below, which resolves the course roles of this very user.
        User currentUser = userRepository.getUser();
        StudentExam studentExam = studentExamRepository.findByIdWithExercisesElseThrow(studentExamId);
        studentExamAccessService.validateExamRequestParametersElseThrow(studentExam, examId, courseId);
        if (!Objects.equals(currentUser.getId(), studentExam.getUser().getId())) {
            throw new AccessForbiddenException("Current user is not the user of the requested student exam");
        }
        checkCourseAccessForTestRunElseThrow(studentExam, examId, courseId, currentUser);
        return ResponseEntity.ok(studentExamAthenaFeedbackService.getAthenaFeedbackUsage(currentUser.getId(), examId, studentExam.isTestRun()));
    }

    /**
     * Ensures that the owner of a test run still has instructor access to the course. Test run ownership survives a
     * role revocation, so ownership alone must not keep an endpoint open for a former instructor. Non test run student
     * exams are unaffected: their parameters were already validated by the caller and the check is an extra query.
     *
     * @param studentExam the student exam the request targets
     * @param examId      the exam id from the path
     * @param courseId    the course id from the path
     * @param currentUser the current user
     */
    private void checkCourseAccessForTestRunElseThrow(StudentExam studentExam, Long examId, Long courseId, User currentUser) {
        if (studentExam.isTestRun()) {
            studentExamAccessService.checkCourseAndExamAccessElseThrow(courseId, examId, currentUser, true, false);
        }
    }

    /**
     * GET /courses/{courseId}/test-exams-per-user
     * Retrieves all StudentExams for test exams of one Course for the current user
     *
     * @param courseId the course to which the student exam belongs to
     * @return all StudentExams (each including its nested exam and course) for test exam for the specified course and user
     */
    @FeatureUsage(UserFeature.EXAM_TEST_EXAMS)
    @GetMapping("courses/{courseId}/test-exams-per-user")
    @EnforceAtLeastStudent
    public ResponseEntity<List<StudentExamDTO>> getStudentExamsForCoursePerUser(@PathVariable Long courseId) {
        User user = userRepository.getUserWithAuthorities();
        studentExamAccessService.checkCourseAccessForStudentElseThrow(courseId, user);

        List<StudentExam> studentExamList = studentExamRepository.findStudentExamsForTestExamsByUserIdAndCourseId(user.getId(), courseId);
        List<StudentExamDTO> studentExamDTOs = studentExamList.stream().map(StudentExamDTO::withExam).toList();

        return ResponseEntity.ok(studentExamDTOs);
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
    @FeatureUsage(UserFeature.EXAM_RESULTS)
    @GetMapping("courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/summary")
    @EnforceAtLeastStudent
    public ResponseEntity<StudentExamForSummaryDTO> getStudentExamForSummary(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long studentExamId) {
        long start = System.currentTimeMillis();
        // Load current membership once for the course access check; the payload is always the student projection.
        User user = userRepository.getUserWithCourseRolesAndAuthorities();

        log.debug("REST request to get the student exam of user {} for exam {}", user.getLogin(), examId);

        // 1st: Get the studentExam from the database
        StudentExam studentExam = studentExamRepository.findByIdWithExercisesAndStudentParticipationsElseThrow(studentExamId);

        // 2nd: Check equal users and access permissions
        if (!user.equals(studentExam.getUser())) {
            throw new AccessForbiddenException("Current user is not the user of the requested student exam");
        }
        studentExamAccessService.checkCourseAndExamAccessElseThrow(courseId, examId, user, studentExam.isTestRun(), false);
        studentExamAccessService.validateExamRequestParametersElseThrow(studentExam, examId, courseId);

        // 3rd: check that the studentExam has been submitted, otherwise /student-exams/{studentExamId}/conduction should be used
        if (!studentExam.isSubmitted()) {
            throw new AccessForbiddenException("You are not allowed to access the summary of a student exam which was NOT submitted!");
        }

        // 3.5th: if the instructor configured a summary publication date, students may only access the summary (incl. exam questions and the PDF export)
        // from that date onwards. Genuine test runs retain immediate access.
        boolean summaryAccessAlwaysAllowed = studentExam.isTestRun();
        if (!summaryAccessAlwaysAllowed && !studentExam.getExam().isExamSummaryPublished()) {
            throw new AccessForbiddenException("The summary of this student exam is not available yet");
        }

        // 4th: Reload the Quiz-Exercises
        examService.loadQuizExercisesForStudentExam(studentExam);

        // 5th fetch participations, submissions and results and connect them to the studentExam
        examService.fetchParticipationsSubmissionsAndResultsForExam(studentExam, ExamReadAudience.STUDENT);

        log.info("getStudentExamForSummary done in {}ms for {} exercises for user {}", System.currentTimeMillis() - start, studentExam.getExercises().size(), user.getLogin());
        // Only a test exam or test run summary offers the AI feedback request - the client hides the button for anything else and
        // StudentExamAthenaFeedbackService rejects it - so a real exam attempt does not read the (lazy) Athena configuration.
        // A test run is an attempt on a real exam, so it has to be named here separately rather than covered by isTestExam().
        if (studentExam.getExam().isTestExam() || studentExam.isTestRun()) {
            courseAthenaConfigRepository.attachTo(studentExam.getExam().getCourse());
        }
        return ResponseEntity.ok(StudentExamForSummaryDTO.of(studentExam));
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/grade-summary : Return student exam result, aggregate points, assessment result
     * for the authenticated owner's student exam and grade calculations if the exam is assessed.
     * <p>
     * Does not return the student exam itself to save bandwidth.
     * <p>
     * See {@link StudentExamWithGradeDTO} for more explanation.
     *
     * @param courseId      the course to which the student exam belongs to
     * @param examId        the exam to which the student exam belongs to
     * @param studentExamId the id of the student exam whose grade summary is requested
     * @return the ResponseEntity with status 200 (OK) and with the StudentExamWithGradeDTO instance without the student exam as body
     */
    @FeatureUsage(UserFeature.EXAM_RESULTS)
    @GetMapping("courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/grade-summary")
    @EnforceAtLeastStudent
    public ResponseEntity<StudentExamWithGradeDTO> getStudentExamGradesForSummary(@PathVariable long courseId, @PathVariable long examId, @PathVariable long studentExamId) {
        User currentUser = userRepository.getUserWithAuthorities();
        StudentExam studentExam = studentExamRepository.findByIdWithExercisesElseThrow(studentExamId);
        if (!currentUser.equals(studentExam.getUser())) {
            throw new AccessForbiddenException("Current user is not the user of the requested student exam");
        }
        studentExamAccessService.checkCourseAndExamAccessElseThrow(courseId, examId, currentUser, studentExam.isTestRun(), false);
        studentExamAccessService.validateExamRequestParametersElseThrow(studentExam, examId, courseId);
        return ResponseEntity.ok(examService.getStudentExamGradesForOwnerSummary(studentExam));
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/student-exams/live-events : Get all exam live events for the student exam of the requesting student.
     * This will include all global events for the exam and all events for the specific student exam.
     *
     * @param courseId the id of the course
     * @param examId   the id of the exam
     * @return the list of exam live events
     */
    @FeatureUsage(UserFeature.EXAM_TAKE)
    @GetMapping("courses/{courseId}/exams/{examId}/student-exams/live-events")
    @EnforceAtLeastStudent
    public ResponseEntity<List<ExamLiveEventBaseDTO>> getExamLiveEvents(@PathVariable Long courseId, @PathVariable Long examId) {
        long start = System.currentTimeMillis();
        User currentUser = userRepository.getUserWithAuthorities();
        log.debug("REST request to get the exam live events for exam {} by user {}", examId, currentUser.getLogin());

        StudentExam studentExam = studentExamRepository.findOneByExamIdAndUserIdElseThrow(examId, currentUser.getId());

        if (studentExam.isTestRun()) {
            throw new BadRequestAlertException("Test runs do not have live events", ENTITY_NAME, "testRunNoLiveEvents");
        }

        studentExamAccessService.checkCourseAndExamAccessElseThrow(courseId, examId, currentUser, studentExam.isTestRun(), false);

        List<ExamLiveEventBaseDTO> examLiveEvents = examLiveEventRepository.findAllByStudentExamIdOrGlobalByExamId(examId, studentExam.getId()).stream().map(ExamLiveEvent::asDTO)
                .toList();

        log.debug("getExamLiveEvents done in {}ms for user {}", System.currentTimeMillis() - start, currentUser.getLogin());
        return ResponseEntity.ok(examLiveEvents);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/test-runs : Find all test runs for the exam
     *
     * @param courseId the id of the course
     * @param examId   the id of the exam
     * @return the list of test runs (each including its nested user)
     */
    @GetMapping("courses/{courseId}/exams/{examId}/test-runs")
    @EnforceAtLeastStudent
    @ManualConfig
    public ResponseEntity<List<StudentExamDTO>> findAllTestRunsForExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to find all test runs for exam {}", examId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        List<StudentExam> testRuns = studentExamRepository.findAllTestRunsByExamId(examId);
        List<StudentExamDTO> testRunDTOs = testRuns.stream().map(StudentExamDTO::withUser).toList();
        return ResponseEntity.ok(testRunDTOs);
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/test-run : Create a test run
     *
     * @param courseId             the id of the course
     * @param examId               the id of the exam
     * @param testRunConfiguration the desired exam id, exercise ids (in persistence order) and working time for the test run
     * @return the created test run student exam (including its nested user), consistent with {@link #findAllTestRunsForExam}
     */
    @PostMapping({ "courses/{courseId}/exams/{examId}/test-runs", "courses/{courseId}/exams/{examId}/test-run" })
    @EnforceAtLeastStudent
    @ManualConfig
    public ResponseEntity<StudentExamDTO> createTestRun(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody CreateTestRunDTO testRunConfiguration) {
        log.info("REST request to create a test run of exam {}", examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        if (testRunConfiguration.examId() != examId) {
            throw new BadRequestException();
        }

        Exam exam = examRepository.findByIdElseThrow(examId);
        List<Long> exerciseIds = testRunConfiguration.exerciseIds() != null ? testRunConfiguration.exerciseIds() : List.of();
        StudentExam testRun = studentExamService.createTestRun(exam, exerciseIds, testRunConfiguration.workingTime());
        return ResponseEntity.ok(StudentExamDTO.withUser(testRun));
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/student-exams/assess-unsubmitted-and-empty-student-exams : Assess unsubmitted student exams and empty submissions.
     * <p>
     * Finds student exams which the students did not submit on time i.e. {@link StudentExam#isSubmitted()} is false and assesses all exercises with 0 points in
     * {@link StudentExamService#assessUnsubmittedStudentExams}.
     * Additionally assess all empty exercises with 0 points in {@link StudentExamService#assessEmptySubmissionsOfStudentExams}.
     * <p>
     * NOTE: A result with 0 points is only added if no other result is present for the latest submission of a relevant StudentParticipation.
     *
     * @param courseId the id of the course
     * @param examId   the id of the exam
     * @return {@link HttpStatus#BAD_REQUEST} if the exam is not over yet | {@link HttpStatus#FORBIDDEN} if the user is not an instructor
     */
    @PostMapping("courses/{courseId}/exams/{examId}/student-exams/assess-unsubmitted-and-empty-student-exams")
    @EnforceAtLeastStudent
    @ManualConfig
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
        testRuns.forEach(testRun -> examDeletionService.deleteTestRun(testRun.getId()));

        final var instructor = userRepository.getUser();
        var assessedUnsubmittedStudentExams = studentExamService.assessUnsubmittedStudentExams(exam, instructor);
        log.info("Graded {} unsubmitted student exams of exam {}", assessedUnsubmittedStudentExams.size(), examId);

        studentExamService.assessEmptySubmissionsOfStudentExams(exam, instructor, assessedUnsubmittedStudentExams);

        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /courses/{courseId}/exams/{examId}/test-runs/{testRunId} : Delete a test run
     *
     * @param courseId  the id of the course
     * @param examId    the id of the exam
     * @param testRunId the id of the student exam of the test run
     * @return the deleted test run student exam
     */
    @DeleteMapping("courses/{courseId}/exams/{examId}/test-runs/{testRunId}")
    @EnforceAtLeastStudent
    @ManualConfig
    public ResponseEntity<Void> deleteTestRun(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long testRunId) {
        log.info("REST request to delete the test run with id {}", testRunId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        StudentExam testRun = studentExamRepository.findById(testRunId).orElseThrow(() -> new EntityNotFoundException("StudentExam", testRunId));
        if (!testRun.isTestRun() || !examId.equals(testRun.getExam().getId())) {
            throw new AccessForbiddenException("The requested student exam is not a test run of this exam");
        }

        examDeletionService.deleteTestRun(testRunId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, testRunId.toString())).build();
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/student-exams/start-exercises : Generate the participation objects
     * for all the student exams belonging to the exam
     *
     * @param courseId the course to which the exam belongs to
     * @param examId   the exam to which the student exam belongs to
     * @return ResponseEntity containing the list of generated participations
     */
    @PostMapping(value = "courses/{courseId}/exams/{examId}/student-exams/start-exercises")
    @EnforceAtLeastStudent
    @ManualConfig
    public ResponseEntity<Void> startExercises(@PathVariable Long courseId, @PathVariable Long examId) {
        long start = System.nanoTime();
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        final Exam exam = examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(examId);

        if (exam.isTestExam()) {
            throw new BadRequestAlertException("Start exercises is only allowed for real exams", "StudentExam", "startExerciseOnlyForRealExams");
        }

        // Only the login is used, for the audit event and the log line, and the login is already in the security
        // context. Loading the user read a row of roughly sixty columns to obtain a value we were holding already.
        String instructorLogin = userRepository.getCurrentUserLogin();
        log.info("REST request to start exercises for student exams of exam {}", examId);
        AuditEvent auditEvent = new AuditEvent(instructorLogin, Constants.PREPARE_EXERCISE_START, "examId=" + examId, "user=" + instructorLogin);
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
    @GetMapping("courses/{courseId}/exams/{examId}/student-exams/start-exercises/status")
    @EnforceAtLeastStudent
    @ManualConfig
    public ResponseEntity<ExamExerciseStartPreparationStatus> getExerciseStartStatus(@PathVariable Long courseId, @PathVariable Long examId) {
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        return ResponseEntity.ok(studentExamService.getExerciseStartStatusOfExam(examId).orElse(null));
    }

    /**
     * PUT /courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/toggle-to-submitted : Toggles the submission
     * status of the specified studentExam to submitted.
     *
     * @param courseId      the course to which the student exams belong to
     * @param examId        the exam to which the student exams belong to
     * @param studentExamId the student exam id where we want to set to be submitted
     * @return the student exam with the new state of submitted and submissionDate
     *         200 if successful
     */
    @PutMapping("courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/toggle-to-submitted")
    @EnforceAtLeastStudent
    @ManualConfig
    public ResponseEntity<StudentExamDTO> submitStudentExam(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long studentExamId) {
        // Only the login is used, for the audit event and the log line, and the login is already in the security
        // context. Loading the user read a row of roughly sixty columns to obtain a value we were holding already.
        String instructorLogin = userRepository.getCurrentUserLogin();
        examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(courseId, examId, studentExamId);

        StudentExam studentExam = studentExamRepository.findById(studentExamId).orElseThrow(() -> new EntityNotFoundException("studentExam", studentExamId));
        if (studentExam.isSubmitted()) {
            throw new BadRequestException();
        }
        ZonedDateTime submissionTime = now();
        if (studentExam.getIndividualEndDateWithGracePeriod().isAfter(submissionTime)) {
            throw new AccessForbiddenException("FORBIDDEN: You tried to toggle a student exam " + studentExamId + " to submitted before the individual end date "
                    + studentExam.getIndividualEndDateWithGracePeriod());
        }

        studentExam.setSubmissionDate(submissionTime);
        studentExam.setSubmitted(true);

        log.info("REST request by user: {} for exam with id {} to set student-exam {} to SUBMITTED", instructorLogin, examId, studentExamId);
        AuditEvent auditEvent = new AuditEvent(instructorLogin, Constants.TOGGLE_STUDENT_EXAM_SUBMITTED, "examId=" + examId, "user=" + instructorLogin,
                "studentExamId=" + studentExamId);
        auditEventRepository.add(auditEvent);

        return ResponseEntity.ok(StudentExamDTO.of(studentExamRepository.save(studentExam)));
    }

    /**
     * PUT /courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/toggle-to-submitted : Toggles the submission
     * status of the specified studentExam to unsubmitted.
     *
     * @param courseId      the course to which the student exams belong to
     * @param examId        the exam to which the student exams belong to
     * @param studentExamId the student exam id where we want to set it to be unsubmitted
     * @return the student exam with the new state of submitted and submissionDate
     *         200 if successful
     */
    @PutMapping("courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/toggle-to-unsubmitted")
    @EnforceAtLeastStudent
    @ManualConfig
    public ResponseEntity<StudentExamDTO> unsubmitStudentExam(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long studentExamId) {
        // Only the login is used, for the audit event and the log line, and the login is already in the security
        // context. Loading the user read a row of roughly sixty columns to obtain a value we were holding already.
        String instructorLogin = userRepository.getCurrentUserLogin();

        examAccessService.checkCourseAndExamAndStudentExamAccessElseThrow(courseId, examId, studentExamId);

        StudentExam studentExam = studentExamRepository.findById(studentExamId).orElseThrow(() -> new EntityNotFoundException("studentExam", studentExamId));
        if (!studentExam.isSubmitted()) {
            throw new BadRequestException();
        }
        if (studentExam.getIndividualEndDateWithGracePeriod().isAfter(now())) {
            throw new AccessForbiddenException("FORBIDDEN: You tried to toggle a student exam " + studentExamId + " to unsubmitted before the individual end date "
                    + studentExam.getIndividualEndDateWithGracePeriod());
        }

        studentExam.setSubmissionDate(null);
        studentExam.setSubmitted(false);

        log.info("REST request by user: {} for exam with id {} to set student-exam {} to UNSUBMITTED", instructorLogin, examId, studentExamId);
        AuditEvent auditEvent = new AuditEvent(instructorLogin, Constants.TOGGLE_STUDENT_EXAM_UNSUBMITTED, "examId=" + examId, "user=" + instructorLogin,
                "studentExamId=" + studentExamId);
        auditEventRepository.add(auditEvent);

        return ResponseEntity.ok(StudentExamDTO.of(studentExamRepository.save(studentExam)));
    }

    /**
     * GET courses/{courseId}/exams/{examId}/longest-working-time : Returns the value of
     * the longest working time of the exam
     *
     * @param courseId the course to which the student exams belong to
     * @param examId   the exam to which the student exams belong to
     * @return the longest working time of the exam (in seconds)
     */
    @EnforceAtLeastStudent
    @ManualConfig
    @GetMapping("courses/{courseId}/exams/{examId}/longest-working-time")
    public ResponseEntity<Integer> getLongestWorkingTimeForExam(@PathVariable Long courseId, @PathVariable Long examId) {
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        Integer longestWorkingTime = studentExamRepository.findLongestWorkingTimeForExam(examId);
        return ResponseEntity.ok().body(longestWorkingTime);
    }
}
