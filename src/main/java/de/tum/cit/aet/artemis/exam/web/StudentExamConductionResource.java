package de.tum.cit.aet.artemis.exam.web;

import static de.tum.cit.aet.artemis.core.config.Constants.EXAM_START_WAIT_TIME_MINUTES;
import static java.time.ZonedDateTime.now;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UsageInteraction;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.core.util.HttpRequestUtils;
import de.tum.cit.aet.artemis.course.repository.CourseAthenaConfigRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.ExamSession;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.conduction.StudentExamForConductionDTO;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exam.service.ExamReadAudience;
import de.tum.cit.aet.artemis.exam.service.ExamService;
import de.tum.cit.aet.artemis.exam.service.ExamSessionService;
import de.tum.cit.aet.artemis.exam.service.StudentExamAccessService;
import de.tum.cit.aet.artemis.exam.service.StudentExamService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;

/** Owner conduction reads and their start/session preparation. */
@Conditional(ExamEnabled.class)
@Lazy
@FeatureUsage(UserFeature.EXAM_CONDUCTION_MANAGEMENT)
@RestController
@RequestMapping("api/exam/")
public class StudentExamConductionResource {

    private static final Logger log = LoggerFactory.getLogger(StudentExamConductionResource.class);

    private final StudentExamRepository studentExamRepository;

    private final StudentExamAccessService studentExamAccessService;

    private final UserRepository userRepository;

    private final ExamService examService;

    private final StudentExamService studentExamService;

    private final ExamSessionService examSessionService;

    private final WebsocketMessagingService websocketMessagingService;

    private final SubmissionPolicyRepository submissionPolicyRepository;

    private final CourseAthenaConfigRepository courseAthenaConfigRepository;

    @Value("${info.studentExamStoreSessionData:#{true}}")
    private boolean storeSessionDataInStudentExamSession;

    public StudentExamConductionResource(StudentExamRepository studentExamRepository, StudentExamAccessService studentExamAccessService, UserRepository userRepository,
            ExamService examService, StudentExamService studentExamService, ExamSessionService examSessionService, WebsocketMessagingService websocketMessagingService,
            SubmissionPolicyRepository submissionPolicyRepository, CourseAthenaConfigRepository courseAthenaConfigRepository) {
        this.studentExamRepository = studentExamRepository;
        this.studentExamAccessService = studentExamAccessService;
        this.userRepository = userRepository;
        this.examService = examService;
        this.studentExamService = studentExamService;
        this.examSessionService = examSessionService;
        this.websocketMessagingService = websocketMessagingService;
        this.submissionPolicyRepository = submissionPolicyRepository;
        this.courseAthenaConfigRepository = courseAthenaConfigRepository;
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
    @FeatureUsage(UserFeature.EXAM_TAKE)
    @UsageInteraction(FeatureInteraction.ACTION)
    @GetMapping("courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/conduction")
    @EnforceAtLeastStudent
    public ResponseEntity<StudentExamForConductionDTO> getStudentExamForConduction(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long studentExamId,
            HttpServletRequest request) {
        return getStudentExamForConduction(courseId, examId, studentExamId, request, ExamReadAudience.STUDENT);
    }

    /**
     * Conducts an instructor's own real attempt, with the same pre-download window and start/session effects.
     *
     * @param courseId      the course to which the exam belongs
     * @param examId        the exam to conduct
     * @param studentExamId the instructor's own real attempt
     * @param request       the request whose headers identify the new exam session
     * @return the instructor-owner conduction projection
     */
    @FeatureUsage(UserFeature.EXAM_TAKE)
    @UsageInteraction(FeatureInteraction.ACTION)
    @GetMapping("courses/{courseId}/exams/{examId}/student-exams/{studentExamId}/conduction/for-instructor")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<StudentExamForConductionDTO> getStudentExamForInstructorConduction(@PathVariable Long courseId, @PathVariable Long examId,
            @PathVariable Long studentExamId, HttpServletRequest request) {
        return getStudentExamForConduction(courseId, examId, studentExamId, request, ExamReadAudience.INSTRUCTOR);
    }

    private ResponseEntity<StudentExamForConductionDTO> getStudentExamForConduction(Long courseId, Long examId, Long studentExamId, HttpServletRequest request,
            ExamReadAudience audience) {
        long start = System.currentTimeMillis();
        User currentUser = userRepository.getUserWithAuthorities();
        StudentExam studentExam = studentExamRepository.findByIdWithExercisesElseThrow(studentExamId);

        if (!currentUser.equals(studentExam.getUser())) {
            throw new AccessForbiddenException("Current user is not the user of the requested student exam");
        }

        studentExamAccessService.validateExamRequestParametersElseThrow(studentExam, examId, courseId);
        if (audience == ExamReadAudience.INSTRUCTOR && studentExam.isTestRun()) {
            throw new AccessForbiddenException("Use test-run conduction for a test run");
        }
        if (studentExam.isTestRun()) {
            // this check is quite expensive, so we only so it for test runs
            studentExamAccessService.checkCourseAndExamAccessElseThrow(courseId, examId, currentUser, true, false);
        }

        // students can not fetch the exam until EXAM_START_WAIT_TIME_MINUTES minutes before the exam start, we use the same constant in the client
        if (now().plusMinutes(EXAM_START_WAIT_TIME_MINUTES).isBefore(studentExam.getExam().getStartDate())) {
            throw new AccessForbiddenException("Students cannot download the student exams until " + EXAM_START_WAIT_TIME_MINUTES + " minutes before the exam start");
        }

        // An instructor deliberately using the student URL receives the same publication gate as any other owner.
        if (Boolean.TRUE.equals(studentExam.isSubmitted()) && !studentExam.getExam().isExamSummaryPublished() && !studentExam.isTestRun()
                && audience != ExamReadAudience.INSTRUCTOR) {
            throw new AccessForbiddenException("The exam content is not available after submission until the summary is published");
        }

        if (!Boolean.TRUE.equals(studentExam.isStarted())) {
            websocketMessagingService.sendMessage(ExamWebsocketTopics.EXAM_STARTED.at(examId), "");
        }

        prepareStudentExamForConduction(request, studentExam, audience);

        log.info("getStudentExamForConduction done in {}ms for {} exercises for user {}", System.currentTimeMillis() - start, studentExam.getExercises().size(),
                currentUser.getLogin());
        return ResponseEntity.ok(StudentExamForConductionDTO.of(studentExam));
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/test-runs/{testRunId}/conduction : Find a specific test run for conduction.
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
    @UsageInteraction(FeatureInteraction.ACTION)
    @GetMapping("courses/{courseId}/exams/{examId}/test-runs/{testRunId}/conduction")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<StudentExamForConductionDTO> getTestRunForConduction(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long testRunId,
            HttpServletRequest request) {
        long start = System.currentTimeMillis();
        User currentUser = userRepository.getUserWithAuthorities();
        log.debug("REST request to get the test run for exam {} with id {}", examId, testRunId);

        // 1st: load the testRun with all associated exercises
        StudentExam testRun = studentExamRepository.findWithExercisesById(testRunId).orElseThrow(() -> new EntityNotFoundException("StudentExam", testRunId));
        // the conduction response reports whether the student may request AI feedback, and the query above no longer
        // drags the configuration along with every course it touches
        courseAthenaConfigRepository.attachTo(testRun.getExam().getCourse());

        if (!currentUser.equals(testRun.getUser())) {
            throw new ConflictException("Current user is not the user of the test run", "StudentExam", "userMismatch");
        }

        if (!testRun.isTestRun() || !examId.equals(testRun.getExam().getId())) {
            throw new AccessForbiddenException("The requested student exam is not a test run of this exam");
        }

        studentExamAccessService.validateExamRequestParametersElseThrow(testRun, examId, courseId);
        studentExamAccessService.checkCourseAndExamAccessElseThrow(courseId, examId, currentUser, true, false);
        prepareStudentExamForConduction(request, testRun, ExamReadAudience.INSTRUCTOR);

        log.info("getTestRunForConduction done in {}ms for {} exercises for user {}", System.currentTimeMillis() - start, testRun.getExercises().size(), currentUser.getLogin());
        return ResponseEntity.ok(StudentExamForConductionDTO.of(testRun));
    }

    /**
     * Sets the started flag and initial started date.
     * Calls {@link ExamService#fetchParticipationsSubmissionsAndResultsForExam} to set up the exercises.
     * Starts an exam session for the request
     * Filters out unnecessary attributes.
     *
     * @param request     the http request for the conduction
     * @param currentUser the current user
     * @param studentExam the student exam to be prepared
     */
    private void prepareStudentExamForConduction(HttpServletRequest request, StudentExam studentExam, ExamReadAudience audience) {

        // In case the studentExam is not yet started, a new participation with a specific initialization date should be created - isStarted uses Boolean
        // Test runs are already prepared when they are created.
        if (studentExam.isTestExam() && !studentExam.isTestRun()) {
            boolean setupTestExamNeeded = studentExam.isStarted() == null || !studentExam.isStarted();
            if (setupTestExamNeeded) {

                // Set up new participations for the Exercises
                studentExamService.setUpTestExamExerciseParticipationsAndSubmissions(studentExam);
            }
        }

        if (!Boolean.TRUE.equals(studentExam.isStarted()) || studentExam.getStartedDate() == null) {
            // Mark the student exam as started with now as the start date if it was not started before
            var startDate = studentExam.getStartedDate() != null ? studentExam.getStartedDate() : now();
            studentExam.setStartedAndStartDate(startDate);
            // send those changes in a modifying query to the database
            studentExamRepository.startStudentExam(studentExam.getId(), startDate);
        }

        var programmingExercises = studentExam.getExercises().stream().filter(exercise -> exercise instanceof ProgrammingExercise).map(exercise -> (ProgrammingExercise) exercise)
                .collect(Collectors.toSet());
        if (!programmingExercises.isEmpty()) {
            // Load all potential submission policies from the database
            var submissionPolicies = submissionPolicyRepository.findAllByProgrammingExerciseIds(programmingExercises.stream().map(DomainObject::getId).collect(Collectors.toSet()));
            // Add the policy to their respective programming exercise
            if (!submissionPolicies.isEmpty()) {
                for (var programmingExercise : programmingExercises) {
                    programmingExercise.setSubmissionPolicy(
                            submissionPolicies.stream().filter(policy -> policy.getProgrammingExercise().getId().equals(programmingExercise.getId())).findFirst().orElse(null));
                }
            }
        }

        // remove build script and config information from the student exam
        for (var programmingExercise : programmingExercises) {
            programmingExercise.filterSensitiveInformation();
        }

        // Load quizzes from database, because they include lazy relationships
        examService.loadQuizExercisesForStudentExam(studentExam);

        // Fetch participations, submissions and results and connect them to the studentExam
        examService.fetchParticipationsSubmissionsAndResultsForExam(studentExam, audience);

        // Create new exam session
        createNewExamSession(request, studentExam);
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
        final String userAgent = !storeSessionDataInStudentExamSession ? null : request.getHeader(HttpHeaders.USER_AGENT);
        ExamSession examSession = this.examSessionService.startExamSession(studentExam, browserFingerprint, userAgent, instanceId, ipAddress);
        examSession.hideDetails();
        examSession.setInitialSession(this.examSessionService.checkExamSessionIsInitial(studentExam.getId()));
        studentExam.setExamSessions(Set.of(examSession));
    }

}
