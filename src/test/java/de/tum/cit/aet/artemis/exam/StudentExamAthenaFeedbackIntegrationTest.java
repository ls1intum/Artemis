package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.athena.AbstractAthenaTest;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.AthenaFeedbackUsageDTO;
import de.tum.cit.aet.artemis.exam.service.StudentExamAthenaFeedbackService;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamFactory;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Integration test for the Athena feedback request in {@link StudentExamAthenaFeedbackService#requestAthenaFeedback}:
 * happy path, real-exam rejection, cross-attempt rate limit, mixed-batch dispatch when one submission already has
 * an Athena result, and unsubmitted exam.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StudentExamAthenaFeedbackIntegrationTest extends AbstractAthenaTest {

    private static final String TEST_PREFIX = "seathena";

    @Autowired
    private StudentExamAthenaFeedbackService studentExamAthenaFeedbackService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    private Course course;

    private User student;

    private User otherStudent;

    private User instructor;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 1);
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        otherStudent = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
    }

    /**
     * Builds a submitted test run of a real exam for the instructor, with one text participation carrying the given
     * answer. Test-run participations are marked as such, which is how they are looked up again for the request.
     */
    private StudentExam createSubmittedTestRun(Exam realExam, TextExercise textExercise, String answer) {
        StudentExam testRun = ExamFactory.generateExamTestRun(realExam);
        testRun.setUser(instructor);
        testRun.addExercise(textExercise);

        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, instructor.getLogin());
        addTextSubmission(participation, answer);
        // the request looks the participations of a test run up by this flag, so the fixture has to set it the same way
        // the real test run conduction does - and only after the submission, while the collection is still initialized
        participation.setTestRun(true);
        participation = studentParticipationRepository.save(participation);

        testRun.getStudentParticipations().add(participation);
        testRun = studentExamRepository.save(testRun);
        testRun.setSubmitted(true);
        testRun.setSubmissionDate(ZonedDateTime.now());
        studentExamRepository.submitStudentExam(testRun.getId(), ZonedDateTime.now());
        detachExerciseParticipationsCollection(testRun);
        return testRun;
    }

    /**
     * Points the in-memory exercise graph at the course instance that carries the Athena config. In production the
     * student exam is loaded with {@code course.athenaConfig} fetched eagerly, which is what
     * {@code Exercise#getAllowFeedbackRequests} reads.
     */
    private void attachAthenaEnabledCourseTo(TextExercise textExercise) {
        enableAthenaForCourse();
        textExercise.getExerciseGroup().getExam().setCourse(course);
    }

    private Exam createRunningRealExam() {
        Exam realExam = examUtilService.addExam(course);
        realExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
        realExam.setStartDate(ZonedDateTime.now().minusHours(1));
        realExam.setEndDate(ZonedDateTime.now().plusHours(1));
        return examRepository.save(realExam);
    }

    private static void detachExerciseParticipationsCollection(StudentExam studentExam) {
        for (Exercise exercise : studentExam.getExercises()) {
            exercise.setStudentParticipations(new HashSet<>());
        }
    }

    private void enableAthenaForCourse() {
        var athenaConfig = new CourseAthenaConfig();
        athenaConfig.setCourse(course);
        athenaConfig.setFormativeFeedbackEnabled(true);
        course.setAthenaConfig(athenaConfig);
        courseRepository.save(course);
    }

    private TextExercise addTextExerciseToExam(Exam exam) {
        var exerciseGroup = examUtilService.addExerciseGroupsAndExercisesToExam(exam, false).getExerciseGroups().getFirst();
        return textExerciseUtilService.createTextExerciseForExam(exerciseGroup);
    }

    private void seedAttemptWithAthenaResult(Exam testExam, TextExercise textExercise) {
        StudentExam attempt = examUtilService.addStudentExamForTestExam(testExam, student);
        attempt.addExercise(textExercise);
        attempt.setSubmitted(true);
        attempt.setSubmissionDate(ZonedDateTime.now().minusMinutes(30));
        // the cap counts reserved attempts, not just successful results (see StudentExamAthenaFeedbackService), so a
        // "prior attempt" fixture must reserve its slot the same way a real request would
        attempt.setAthenaFeedbackRequestedDate(ZonedDateTime.now().minusMinutes(30));

        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
        TextSubmission submission = new TextSubmission();
        submission.setText("Prior attempt submission.");
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now().minusMinutes(30));
        participationUtilService.addSubmission(participation, submission);

        saveAthenaResult(submission, textExercise.getId(), ZonedDateTime.now().minusMinutes(29));

        attempt.getStudentParticipations().add(participation);
        studentExamRepository.save(attempt);
    }

    private TextSubmission addTextSubmission(StudentParticipation participation, String text) {
        TextSubmission submission = new TextSubmission();
        submission.setText(text);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());
        return (TextSubmission) participationUtilService.addSubmission(participation, submission);
    }

    private void addModelingSubmission(StudentParticipation participation) {
        ModelingSubmission submission = new ModelingSubmission();
        submission.setModel("{\"version\":\"4.0.0\",\"type\":\"ClassDiagram\",\"nodes\":[{\"id\":\"n1\"}],\"edges\":[]}");
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());
        participationUtilService.addSubmission(participation, submission);
    }

    private void saveAthenaResult(TextSubmission submission, long exerciseId, ZonedDateTime completionDate) {
        Result athenaResult = new Result();
        athenaResult.setExerciseId(exerciseId);
        athenaResult.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        athenaResult.setSuccessful(true);
        athenaResult.setScore(100D);
        athenaResult.setCompletionDate(completionDate);
        athenaResult.setSubmission(submission);
        resultRepository.save(athenaResult);
    }

    @Nested
    class DispatchHappyPath {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void requestAthenaFeedback_shouldDispatchForTextParticipation() {
            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);
            TextExercise textExercise = addTextExerciseToExam(testExam);
            enableAthenaForCourse();

            athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("text");

            StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam, student);
            studentExam.addExercise(textExercise);

            StudentParticipation textParticipation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
            addTextSubmission(textParticipation, "Meaningful text answer from the student.");

            studentExam.getStudentParticipations().add(textParticipation);
            studentExam = studentExamRepository.save(studentExam);

            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(ZonedDateTime.now());
            studentExamRepository.submitStudentExam(studentExam.getId(), ZonedDateTime.now());

            detachExerciseParticipationsCollection(studentExam);

            studentExamAthenaFeedbackService.requestAthenaFeedback(studentExam, student);

            verify(resultWebsocketService, timeout(5000).times(2)).broadcastNewResult(eq(textParticipation), any(Result.class));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void requestAthenaFeedback_shouldDispatchForModelingParticipation() {
            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);
            testExam = examUtilService.addTextModelingProgrammingExercisesToExam(testExam, false, false);
            ModelingExercise modelingExercise = (ModelingExercise) testExam.getExerciseGroups().get(1).getExercises().iterator().next();
            enableAthenaForCourse();

            athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("modeling");

            StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam, student);
            studentExam.addExercise(modelingExercise);

            StudentParticipation modelingParticipation = participationUtilService.createAndSaveParticipationForExercise(modelingExercise, student.getLogin());
            addModelingSubmission(modelingParticipation);

            studentExam.getStudentParticipations().add(modelingParticipation);
            studentExam = studentExamRepository.save(studentExam);

            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(ZonedDateTime.now());
            studentExamRepository.submitStudentExam(studentExam.getId(), ZonedDateTime.now());

            detachExerciseParticipationsCollection(studentExam);

            studentExamAthenaFeedbackService.requestAthenaFeedback(studentExam, student);

            verify(resultWebsocketService, timeout(5000).times(2)).broadcastNewResult(eq(modelingParticipation), any(Result.class));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void requestAthenaFeedback_shouldDispatchForInstructorTestRun() {
            Exam realExam = createRunningRealExam();
            TextExercise textExercise = addTextExerciseToExam(realExam);
            attachAthenaEnabledCourseTo(textExercise);

            athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("text");

            StudentExam testRun = createSubmittedTestRun(realExam, textExercise, "Meaningful text answer from the instructor.");
            StudentParticipation testRunParticipation = testRun.getStudentParticipations().iterator().next();

            studentExamAthenaFeedbackService.requestAthenaFeedback(testRun, instructor);

            verify(resultWebsocketService, timeout(5000).times(2)).broadcastNewResult(eq(testRunParticipation), any(Result.class));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void requestAthenaFeedback_shouldStillDispatchPeersWhenOneSubmissionAlreadyHasAthenaResult() {
            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);
            testExam = examUtilService.addTextModelingProgrammingExercisesToExam(testExam, false, false);

            TextExercise textExercise = (TextExercise) testExam.getExerciseGroups().getFirst().getExercises().iterator().next();
            ModelingExercise modelingExercise = (ModelingExercise) testExam.getExerciseGroups().get(1).getExercises().iterator().next();
            enableAthenaForCourse();

            athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("modeling");

            StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam, student);
            studentExam.addExercise(textExercise);
            studentExam.addExercise(modelingExercise);

            // text submission already has an Athena result - must not block the modeling peer
            StudentParticipation textParticipation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
            TextSubmission textSubmission = addTextSubmission(textParticipation, "Answer for which Athena feedback was already generated.");
            saveAthenaResult(textSubmission, textExercise.getId(), ZonedDateTime.now());

            // modeling submission has no result yet - should still get dispatched
            StudentParticipation modelingParticipation = participationUtilService.createAndSaveParticipationForExercise(modelingExercise, student.getLogin());
            addModelingSubmission(modelingParticipation);

            studentExam.getStudentParticipations().add(textParticipation);
            studentExam.getStudentParticipations().add(modelingParticipation);
            studentExam = studentExamRepository.save(studentExam);

            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(ZonedDateTime.now());
            studentExamRepository.submitStudentExam(studentExam.getId(), ZonedDateTime.now());

            detachExerciseParticipationsCollection(studentExam);

            studentExamAthenaFeedbackService.requestAthenaFeedback(studentExam, student);

            verify(resultWebsocketService, timeout(5000).times(2)).broadcastNewResult(eq(modelingParticipation), any(Result.class));
        }
    }

    @Nested
    class Rejections {

        @Test
        void requestAthenaFeedback_shouldRejectForNonTestExam() {
            Exam realExam = examUtilService.addExam(course);
            realExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            realExam.setStartDate(ZonedDateTime.now().minusHours(1));
            realExam.setEndDate(ZonedDateTime.now().plusHours(1));
            realExam = examRepository.save(realExam);
            realExam = examUtilService.addTextModelingProgrammingExercisesToExam(realExam, false, false);

            TextExercise textExercise = (TextExercise) realExam.getExerciseGroups().getFirst().getExercises().iterator().next();

            StudentExam studentExam = examUtilService.addStudentExamWithUser(realExam, student);
            studentExam.addExercise(textExercise);
            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(ZonedDateTime.now());
            studentExam = studentExamRepository.save(studentExam);

            StudentExam finalStudentExam = studentExam;
            assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> studentExamAthenaFeedbackService.requestAthenaFeedback(finalStudentExam, student));
        }

        @Test
        void requestAthenaFeedback_shouldRejectUnsubmittedExam() {
            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);

            StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam, student);
            // Do NOT mark as submitted

            assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> studentExamAthenaFeedbackService.requestAthenaFeedback(studentExam, student));
        }

        @Test
        void requestAthenaFeedback_shouldRejectWhenCourseAthenaConfigIsDisabled() {
            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);
            TextExercise textExercise = addTextExerciseToExam(testExam);
            // intentionally do NOT enable course-level Athena formative feedback

            StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam, student);
            studentExam.addExercise(textExercise);

            StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
            addTextSubmission(participation, "Submission for an exercise without a configured feedback module.");

            studentExam.getStudentParticipations().add(participation);
            studentExam = studentExamRepository.save(studentExam);

            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(ZonedDateTime.now());
            studentExamRepository.submitStudentExam(studentExam.getId(), ZonedDateTime.now());

            detachExerciseParticipationsCollection(studentExam);

            StudentExam finalStudentExam = studentExam;
            assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> studentExamAthenaFeedbackService.requestAthenaFeedback(finalStudentExam, student));
        }

        @Test
        void requestAthenaFeedback_shouldRejectAndNotConsumeCapSlotWhenOnlySubmissionIsEmpty() {
            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);
            TextExercise textExercise = addTextExerciseToExam(testExam);
            enableAthenaForCourse();

            StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam, student);
            studentExam.addExercise(textExercise);

            // the feedback generator skips empty submissions silently, so this attempt must be rejected rather than
            // reserving a cap slot for a request that will never generate feedback
            StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
            addTextSubmission(participation, "");

            studentExam.getStudentParticipations().add(participation);
            studentExam = studentExamRepository.save(studentExam);

            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(ZonedDateTime.now());
            studentExamRepository.submitStudentExam(studentExam.getId(), ZonedDateTime.now());

            detachExerciseParticipationsCollection(studentExam);

            StudentExam finalStudentExam = studentExam;
            assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> studentExamAthenaFeedbackService.requestAthenaFeedback(finalStudentExam, student));

            AthenaFeedbackUsageDTO usage = studentExamAthenaFeedbackService.getAthenaFeedbackUsage(student.getId(), testExam.getId(), false);
            assertThat(usage.used()).isZero();
        }

        @Test
        void requestAthenaFeedback_shouldRejectAndNotConsumeCapSlotWhenOnlySubmissionAlreadyHasAthenaResult() {
            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);
            TextExercise textExercise = addTextExerciseToExam(testExam);
            enableAthenaForCourse();

            StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam, student);
            studentExam.addExercise(textExercise);

            // a repeated or recovery request must not consume a cap slot without dispatching any new generation: the
            // generator silently skips a submission that already has an Athena result, so this attempt must be
            // rejected up front instead of reserving a slot for a request that will never generate feedback
            StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
            TextSubmission submission = addTextSubmission(participation, "Answer for which Athena feedback was already generated.");
            saveAthenaResult(submission, textExercise.getId(), ZonedDateTime.now());

            studentExam.getStudentParticipations().add(participation);
            studentExam = studentExamRepository.save(studentExam);

            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(ZonedDateTime.now());
            studentExamRepository.submitStudentExam(studentExam.getId(), ZonedDateTime.now());

            detachExerciseParticipationsCollection(studentExam);

            StudentExam finalStudentExam = studentExam;
            assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> studentExamAthenaFeedbackService.requestAthenaFeedback(finalStudentExam, student));

            AthenaFeedbackUsageDTO usage = studentExamAthenaFeedbackService.getAthenaFeedbackUsage(student.getId(), testExam.getId(), false);
            assertThat(usage.used()).isZero();
        }

        @Test
        void requestAthenaFeedback_shouldRejectAndNotConsumeCapSlotWhenAthenaIsDisabled() {
            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);
            TextExercise textExercise = addTextExerciseToExam(testExam);
            enableAthenaForCourse();

            StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam, student);
            studentExam.addExercise(textExercise);

            StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
            addTextSubmission(participation, "Meaningful text answer from the student.");

            studentExam.getStudentParticipations().add(participation);
            studentExam = studentExamRepository.save(studentExam);

            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(ZonedDateTime.now());
            studentExamRepository.submitStudentExam(studentExam.getId(), ZonedDateTime.now());

            detachExerciseParticipationsCollection(studentExam);

            // The type-specific feedback APIs (text/modeling) are wired independently of the Athena profile, so they
            // stay present even when Athena itself is disabled. Only AthenaFeedbackApi is gated on the Athena
            // profile - simulate that here to reproduce the case where the guard must reject before reserving a slot.
            Object originalAthenaFeedbackApi = ReflectionTestUtils.getField(studentExamAthenaFeedbackService, "athenaFeedbackApi");
            ReflectionTestUtils.setField(studentExamAthenaFeedbackService, "athenaFeedbackApi", Optional.empty());
            try {
                StudentExam finalStudentExam = studentExam;
                assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> studentExamAthenaFeedbackService.requestAthenaFeedback(finalStudentExam, student));

                AthenaFeedbackUsageDTO usage = studentExamAthenaFeedbackService.getAthenaFeedbackUsage(student.getId(), testExam.getId(), false);
                assertThat(usage.used()).isZero();
            }
            finally {
                ReflectionTestUtils.setField(studentExamAthenaFeedbackService, "athenaFeedbackApi", originalAthenaFeedbackApi);
            }
        }
    }

    @Nested
    class RateLimit {

        @Test
        void requestAthenaFeedback_shouldRejectWhenRateLimitExceeded() {
            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);
            TextExercise textExercise = addTextExerciseToExam(testExam);
            enableAthenaForCourse();

            for (int i = 0; i < 10; i++) {
                seedAttemptWithAthenaResult(testExam, textExercise);
            }

            StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam, student);
            studentExam.addExercise(textExercise);

            StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
            addTextSubmission(participation, "This is the eleventh attempt and should not receive Athena feedback.");

            studentExam.getStudentParticipations().add(participation);
            studentExam = studentExamRepository.save(studentExam);

            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(ZonedDateTime.now());
            studentExamRepository.submitStudentExam(studentExam.getId(), ZonedDateTime.now());

            detachExerciseParticipationsCollection(studentExam);

            StudentExam finalStudentExam = studentExam;
            assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> studentExamAthenaFeedbackService.requestAthenaFeedback(finalStudentExam, student));
        }
    }

    @Nested
    class Idempotency {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void requestAthenaFeedback_shouldRejectDuplicateRequestForSameAttempt() {
            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);
            TextExercise textExercise = addTextExerciseToExam(testExam);
            enableAthenaForCourse();

            athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("text");

            StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam, student);
            studentExam.addExercise(textExercise);

            StudentParticipation textParticipation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
            addTextSubmission(textParticipation, "Meaningful text answer from the student.");

            studentExam.getStudentParticipations().add(textParticipation);
            studentExam = studentExamRepository.save(studentExam);

            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(ZonedDateTime.now());
            studentExamRepository.submitStudentExam(studentExam.getId(), ZonedDateTime.now());

            detachExerciseParticipationsCollection(studentExam);

            // the first request reserves the attempt's slot and dispatches
            studentExamAthenaFeedbackService.requestAthenaFeedback(studentExam, student);
            verify(resultWebsocketService, timeout(5000).times(2)).broadcastNewResult(eq(textParticipation), any(Result.class));

            // a duplicate request for the very same attempt (e.g. a retried click, or a second concurrent request that read the
            // same pre-reservation state) must be rejected rather than dispatching Athena generation a second time
            StudentExam finalStudentExam = studentExam;
            assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> studentExamAthenaFeedbackService.requestAthenaFeedback(finalStudentExam, student));

            // still only the single dispatch from the first request
            verify(resultWebsocketService, timeout(2000).times(2)).broadcastNewResult(eq(textParticipation), any(Result.class));
        }

        @Test
        void reserveAthenaFeedbackRequestIfBelowCap_shouldOnlyLetOneOfTwoAttemptsRacingForTheLastSlotThrough() {
            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);

            StudentExam firstAttempt = examUtilService.addStudentExamForTestExam(testExam, student);
            StudentExam secondAttempt = examUtilService.addStudentExamForTestExam(testExam, student);

            ZonedDateTime now = ZonedDateTime.now();
            // simulates two attempts racing for the last remaining slot of a cap of 1: without an atomic check-and-reserve,
            // both could observe "0 used, cap 1" and both succeed
            int firstReserved = studentExamRepository.reserveAthenaFeedbackRequestIfBelowCap(firstAttempt.getId(), student.getId(), testExam.getId(), false, now, 1);
            int secondReserved = studentExamRepository.reserveAthenaFeedbackRequestIfBelowCap(secondAttempt.getId(), student.getId(), testExam.getId(), false, now, 1);

            assertThat(firstReserved).isEqualTo(1);
            assertThat(secondReserved).isZero();
        }

        @Test
        void reserveAthenaFeedbackRequestIfBelowCap_shouldOnlyLetOneOfTwoConcurrentAttemptsForDifferentAttemptsThrough() throws Exception {
            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);

            StudentExam firstAttempt = examUtilService.addStudentExamForTestExam(testExam, student);
            StudentExam secondAttempt = examUtilService.addStudentExamForTestExam(testExam, student);

            Long firstAttemptId = firstAttempt.getId();
            Long secondAttemptId = secondAttempt.getId();
            Long studentId = student.getId();
            Long examId = testExam.getId();

            // Genuine concurrency (separate threads, each opening its own transaction/connection) is required to expose
            // the bug this test guards against: a plain "count reserved attempts, then update the target row" sequence
            // lets two transactions targeting different attempt rows each lock only their own row, so both can observe
            // the same pre-reservation count and both succeed past the cap. Calling the same method twice sequentially
            // from one thread cannot reproduce this, since the first call's transaction always commits before the
            // second one starts.
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            try {
                Callable<Integer> reserveFirstAttempt = () -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return studentExamRepository.reserveAthenaFeedbackRequestIfBelowCap(firstAttemptId, studentId, examId, false, ZonedDateTime.now(), 1);
                };
                Callable<Integer> reserveSecondAttempt = () -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return studentExamRepository.reserveAthenaFeedbackRequestIfBelowCap(secondAttemptId, studentId, examId, false, ZonedDateTime.now(), 1);
                };

                Future<Integer> firstReservedFuture = executor.submit(reserveFirstAttempt);
                Future<Integer> secondReservedFuture = executor.submit(reserveSecondAttempt);
                assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
                startLatch.countDown();

                int firstReserved = firstReservedFuture.get(10, TimeUnit.SECONDS);
                int secondReserved = secondReservedFuture.get(10, TimeUnit.SECONDS);

                assertThat(firstReserved + secondReserved).isEqualTo(1);
            }
            finally {
                executor.shutdownNow();
            }
        }
    }

    @Nested
    class UsageCounting {

        @Test
        void getAthenaFeedbackUsage_shouldReturnZeroWhenNoAthenaResultsExist() {
            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);

            AthenaFeedbackUsageDTO usage = studentExamAthenaFeedbackService.getAthenaFeedbackUsage(student.getId(), testExam.getId(), false);

            assertThat(usage.used()).isZero();
            assertThat(usage.limit()).isPositive();
        }

        @Test
        void getAthenaFeedbackUsage_shouldCountAttemptsWithSuccessfulAthenaResult() {
            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);
            TextExercise textExercise = addTextExerciseToExam(testExam);

            seedAttemptWithAthenaResult(testExam, textExercise);
            seedAttemptWithAthenaResult(testExam, textExercise);
            seedAttemptWithAthenaResult(testExam, textExercise);

            AthenaFeedbackUsageDTO usage = studentExamAthenaFeedbackService.getAthenaFeedbackUsage(student.getId(), testExam.getId(), false);

            assertThat(usage.used()).isEqualTo(3L);
        }

        @Test
        void getAthenaFeedbackUsage_shouldIgnoreAthenaResultsFromOtherExams() {
            Exam otherExam = examUtilService.addTestExam(course);
            otherExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            otherExam.setStartDate(ZonedDateTime.now().minusHours(1));
            otherExam.setEndDate(ZonedDateTime.now().plusHours(1));
            otherExam = examRepository.save(otherExam);
            TextExercise otherTextExercise = addTextExerciseToExam(otherExam);
            seedAttemptWithAthenaResult(otherExam, otherTextExercise);

            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);

            AthenaFeedbackUsageDTO usage = studentExamAthenaFeedbackService.getAthenaFeedbackUsage(student.getId(), testExam.getId(), false);

            assertThat(usage.used()).isZero();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void getAthenaFeedbackUsage_shouldCountTestRunsInTheirOwnBucket() {
            Exam realExam = createRunningRealExam();
            TextExercise textExercise = addTextExerciseToExam(realExam);
            attachAthenaEnabledCourseTo(textExercise);

            athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("text");

            StudentExam testRun = createSubmittedTestRun(realExam, textExercise, "Meaningful text answer from the instructor.");
            studentExamAthenaFeedbackService.requestAthenaFeedback(testRun, instructor);

            assertThat(studentExamAthenaFeedbackService.getAthenaFeedbackUsage(instructor.getId(), realExam.getId(), true).used()).isEqualTo(1L);
            assertThat(studentExamAthenaFeedbackService.getAthenaFeedbackUsage(instructor.getId(), realExam.getId(), false).used()).isZero();
        }
    }

    @Nested
    class RestEndpoints {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void restRequestAthenaFeedback_shouldReturnOkAndInvokeApis() throws Exception {
            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);
            testExam = examUtilService.addTextModelingProgrammingExercisesToExam(testExam, false, false);

            TextExercise textExercise = (TextExercise) testExam.getExerciseGroups().getFirst().getExercises().iterator().next();
            enableAthenaForCourse();

            athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("text");

            StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam, student);
            studentExam.addExercise(textExercise);

            StudentParticipation textParticipation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
            addTextSubmission(textParticipation, "Meaningful text answer from the student.");

            studentExam.getStudentParticipations().add(textParticipation);
            studentExam = studentExamRepository.save(studentExam);

            studentExamRepository.submitStudentExam(studentExam.getId(), ZonedDateTime.now());

            String url = "/api/exam/courses/" + course.getId() + "/exams/" + testExam.getId() + "/student-exams/" + studentExam.getId() + "/request-feedback";
            request.postWithoutResponseBody(url, null, HttpStatus.OK);

            verify(resultWebsocketService, timeout(5000).times(2)).broadcastNewResult(eq(textParticipation), any(Result.class));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
        void restRequestAthenaFeedback_shouldReturnForbiddenWhenCurrentUserIsNotOwner() throws Exception {
            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);
            TextExercise textExercise = addTextExerciseToExam(testExam);

            StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam, student);
            studentExam.addExercise(textExercise);
            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(ZonedDateTime.now());
            studentExam = studentExamRepository.save(studentExam);

            String url = "/api/exam/courses/" + course.getId() + "/exams/" + testExam.getId() + "/student-exams/" + studentExam.getId() + "/request-feedback";
            request.postWithoutResponseBody(url, null, HttpStatus.FORBIDDEN);

            // Silence unused-field warning: otherStudent is the user authenticated via @WithMockUser.
            assertThat(otherStudent.getLogin()).isEqualTo(TEST_PREFIX + "student2");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void restGetAthenaFeedbackUsage_shouldReturnDto() throws Exception {
            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);
            TextExercise textExercise = addTextExerciseToExam(testExam);

            seedAttemptWithAthenaResult(testExam, textExercise);
            seedAttemptWithAthenaResult(testExam, textExercise);

            StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam, student);
            studentExam.addExercise(textExercise);
            studentExam = studentExamRepository.save(studentExam);

            String url = "/api/exam/courses/" + course.getId() + "/exams/" + testExam.getId() + "/student-exams/" + studentExam.getId() + "/athena-feedback-usage";
            AthenaFeedbackUsageDTO usage = request.get(url, HttpStatus.OK, AthenaFeedbackUsageDTO.class);

            assertThat(usage).isNotNull();
            assertThat(usage.used()).isEqualTo(2L);
            assertThat(usage.limit()).isPositive();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
        void restGetAthenaFeedbackUsage_shouldReturnForbiddenWhenCurrentUserIsNotOwner() throws Exception {
            Exam testExam = examUtilService.addTestExam(course);
            testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
            testExam.setStartDate(ZonedDateTime.now().minusHours(1));
            testExam.setEndDate(ZonedDateTime.now().plusHours(1));
            testExam = examRepository.save(testExam);
            TextExercise textExercise = addTextExerciseToExam(testExam);

            StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam, student);
            studentExam.addExercise(textExercise);
            studentExam = studentExamRepository.save(studentExam);

            String url = "/api/exam/courses/" + course.getId() + "/exams/" + testExam.getId() + "/student-exams/" + studentExam.getId() + "/athena-feedback-usage";
            request.get(url, HttpStatus.FORBIDDEN, AthenaFeedbackUsageDTO.class);
        }
    }
}
