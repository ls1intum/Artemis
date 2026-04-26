package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.AthenaFeedbackUsageDTO;
import de.tum.cit.aet.artemis.exam.service.StudentExamService;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.modeling.api.ModelingFeedbackApi;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;
import de.tum.cit.aet.artemis.text.api.TextFeedbackApi;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Integration test for the Athena feedback request in {@link StudentExamService#requestAthenaFeedbackForTestExam}:
 * happy path, real-exam rejection, cross-attempt rate limit, per-submission guard, and unsubmitted exam.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StudentExamAthenaFeedbackIntegrationTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "seathena";

    @MockitoSpyBean
    private TextFeedbackApi textFeedbackApi;

    @MockitoSpyBean
    private ModelingFeedbackApi modelingFeedbackApi;

    @Autowired
    private StudentExamService studentExamService;

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
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private Course course;

    private User student;

    private User otherStudent;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        otherStudent = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        course = courseUtilService.addEmptyCourse();

        doNothing().when(textFeedbackApi).generateAutomaticFeedbackForTestExamAsync(any(StudentParticipation.class), any(TextExercise.class));
        doNothing().when(modelingFeedbackApi).generateAutomaticFeedbackForTestExamAsync(any(StudentParticipation.class), any(ModelingExercise.class));
    }

    private static void detachExerciseParticipationsCollection(StudentExam studentExam) {
        for (Exercise exercise : studentExam.getExercises()) {
            exercise.setStudentParticipations(new HashSet<>());
        }
    }

    private TextExercise addTextExerciseToExam(Exam exam) {
        var exerciseGroup = examUtilService.addExerciseGroupsAndExercisesToExam(exam, false).getExerciseGroups().getFirst();
        return textExerciseUtilService.createTextExerciseForExam(exerciseGroup);
    }

    @Test
    void requestAthenaFeedback_shouldTriggerForTextAndModelingParticipations() {
        Exam testExam = examUtilService.addTestExam(course);
        testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
        testExam.setStartDate(ZonedDateTime.now().minusHours(1));
        testExam.setEndDate(ZonedDateTime.now().plusHours(1));
        testExam = examRepository.save(testExam);
        testExam = examUtilService.addTextModelingProgrammingExercisesToExam(testExam, false, false);

        TextExercise textExercise = (TextExercise) testExam.getExerciseGroups().getFirst().getExercises().iterator().next();
        ModelingExercise modelingExercise = (ModelingExercise) testExam.getExerciseGroups().get(1).getExercises().iterator().next();

        StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam, student);
        studentExam.addExercise(textExercise);
        studentExam.addExercise(modelingExercise);

        StudentParticipation textParticipation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.setText("Meaningful text answer from the student.");
        textSubmission.setSubmitted(true);
        textSubmission.setSubmissionDate(ZonedDateTime.now());
        textSubmission.setParticipation(textParticipation);
        textParticipation.addSubmission(textSubmission);
        studentParticipationRepository.save(textParticipation);

        StudentParticipation modelingParticipation = participationUtilService.createAndSaveParticipationForExercise(modelingExercise, student.getLogin());
        ModelingSubmission modelingSubmission = new ModelingSubmission();
        modelingSubmission.setModel("{\"version\":\"4.0.0\",\"type\":\"ClassDiagram\",\"nodes\":[{\"id\":\"n1\"}],\"edges\":[]}");
        modelingSubmission.setSubmitted(true);
        modelingSubmission.setSubmissionDate(ZonedDateTime.now());
        modelingSubmission.setParticipation(modelingParticipation);
        modelingParticipation.addSubmission(modelingSubmission);
        studentParticipationRepository.save(modelingParticipation);

        studentExam.getStudentParticipations().add(textParticipation);
        studentExam.getStudentParticipations().add(modelingParticipation);
        studentExam = studentExamRepository.save(studentExam);

        studentExam.setSubmitted(true);
        studentExam.setSubmissionDate(ZonedDateTime.now());
        studentExamRepository.submitStudentExam(studentExam.getId(), ZonedDateTime.now());

        detachExerciseParticipationsCollection(studentExam);

        studentExamService.requestAthenaFeedbackForTestExam(studentExam, student);

        verify(textFeedbackApi, times(1)).generateAutomaticFeedbackForTestExamAsync(any(StudentParticipation.class), eq(textExercise));
        verify(modelingFeedbackApi, times(1)).generateAutomaticFeedbackForTestExamAsync(any(StudentParticipation.class), eq(modelingExercise));
    }

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
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> studentExamService.requestAthenaFeedbackForTestExam(finalStudentExam, student));

        verify(textFeedbackApi, never()).generateAutomaticFeedbackForTestExamAsync(any(), any());
        verify(modelingFeedbackApi, never()).generateAutomaticFeedbackForTestExamAsync(any(), any());
    }

    @Test
    void requestAthenaFeedback_shouldRejectWhenRateLimitExceeded() {
        Exam testExam = examUtilService.addTestExam(course);
        testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
        testExam.setStartDate(ZonedDateTime.now().minusHours(1));
        testExam.setEndDate(ZonedDateTime.now().plusHours(1));
        testExam = examRepository.save(testExam);
        TextExercise textExercise = addTextExerciseToExam(testExam);

        for (int i = 0; i < 10; i++) {
            seedAttemptWithAthenaResult(testExam, textExercise);
        }

        StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam, student);
        studentExam.addExercise(textExercise);

        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
        TextSubmission submission = new TextSubmission();
        submission.setText("This is the eleventh attempt and should not receive Athena feedback.");
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setParticipation(participation);
        participation.addSubmission(submission);
        studentParticipationRepository.save(participation);

        studentExam.getStudentParticipations().add(participation);
        studentExam = studentExamRepository.save(studentExam);

        studentExam.setSubmitted(true);
        studentExam.setSubmissionDate(ZonedDateTime.now());
        studentExamRepository.submitStudentExam(studentExam.getId(), ZonedDateTime.now());

        detachExerciseParticipationsCollection(studentExam);

        StudentExam finalStudentExam = studentExam;
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> studentExamService.requestAthenaFeedbackForTestExam(finalStudentExam, student));

        verify(textFeedbackApi, never()).generateAutomaticFeedbackForTestExamAsync(any(), any());
    }

    @Test
    void requestAthenaFeedback_shouldRejectWhenSubmissionAlreadyHasAthenaResult() {
        Exam testExam = examUtilService.addTestExam(course);
        testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
        testExam.setStartDate(ZonedDateTime.now().minusHours(1));
        testExam.setEndDate(ZonedDateTime.now().plusHours(1));
        testExam = examRepository.save(testExam);
        TextExercise textExercise = addTextExerciseToExam(testExam);

        StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam, student);
        studentExam.addExercise(textExercise);

        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
        TextSubmission submission = new TextSubmission();
        submission.setText("Answer for which Athena feedback was already generated.");
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setParticipation(participation);
        participation.addSubmission(submission);
        studentParticipationRepository.save(participation);

        Result athenaResult = new Result();
        athenaResult.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        athenaResult.setSuccessful(true);
        athenaResult.setScore(100D);
        athenaResult.setCompletionDate(ZonedDateTime.now());
        athenaResult.setSubmission(submission);
        resultRepository.save(athenaResult);

        studentExam.getStudentParticipations().add(participation);
        studentExam = studentExamRepository.save(studentExam);

        studentExam.setSubmitted(true);
        studentExam.setSubmissionDate(ZonedDateTime.now());
        studentExamRepository.submitStudentExam(studentExam.getId(), ZonedDateTime.now());

        detachExerciseParticipationsCollection(studentExam);

        StudentExam finalStudentExam = studentExam;
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> studentExamService.requestAthenaFeedbackForTestExam(finalStudentExam, student));

        verify(textFeedbackApi, never()).generateAutomaticFeedbackForTestExamAsync(any(), any());
    }

    private void seedAttemptWithAthenaResult(Exam testExam, TextExercise textExercise) {
        StudentExam attempt = examUtilService.addStudentExamForTestExam(testExam, student);
        attempt.addExercise(textExercise);
        attempt.setSubmitted(true);
        attempt.setSubmissionDate(ZonedDateTime.now().minusMinutes(30));

        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
        TextSubmission submission = new TextSubmission();
        submission.setText("Prior attempt submission.");
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now().minusMinutes(30));
        submission.setParticipation(participation);
        participation.addSubmission(submission);
        studentParticipationRepository.save(participation);

        Result athenaResult = new Result();
        athenaResult.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        athenaResult.setSuccessful(true);
        athenaResult.setScore(100D);
        athenaResult.setCompletionDate(ZonedDateTime.now().minusMinutes(29));
        athenaResult.setSubmission(submission);
        resultRepository.save(athenaResult);

        attempt.getStudentParticipations().add(participation);
        studentExamRepository.save(attempt);
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

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> studentExamService.requestAthenaFeedbackForTestExam(studentExam, student));

        verify(textFeedbackApi, never()).generateAutomaticFeedbackForTestExamAsync(any(), any());
    }

    @Test
    void getAthenaFeedbackUsage_shouldReturnZeroWhenNoAthenaResultsExist() {
        Exam testExam = examUtilService.addTestExam(course);
        testExam.setVisibleDate(ZonedDateTime.now().minusHours(2));
        testExam.setStartDate(ZonedDateTime.now().minusHours(1));
        testExam.setEndDate(ZonedDateTime.now().plusHours(1));
        testExam = examRepository.save(testExam);

        AthenaFeedbackUsageDTO usage = studentExamService.getAthenaFeedbackUsage(student.getId(), testExam.getId());

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

        AthenaFeedbackUsageDTO usage = studentExamService.getAthenaFeedbackUsage(student.getId(), testExam.getId());

        assertThat(usage.used()).isEqualTo(3L);
    }

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
        ModelingExercise modelingExercise = (ModelingExercise) testExam.getExerciseGroups().get(1).getExercises().iterator().next();

        StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam, student);
        studentExam.addExercise(textExercise);
        studentExam.addExercise(modelingExercise);

        StudentParticipation textParticipation = participationUtilService.createAndSaveParticipationForExercise(textExercise, student.getLogin());
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.setText("Meaningful text answer from the student.");
        textSubmission.setSubmitted(true);
        textSubmission.setSubmissionDate(ZonedDateTime.now());
        textSubmission.setParticipation(textParticipation);
        textParticipation.addSubmission(textSubmission);
        studentParticipationRepository.save(textParticipation);

        studentExam.getStudentParticipations().add(textParticipation);
        studentExam = studentExamRepository.save(studentExam);

        studentExamRepository.submitStudentExam(studentExam.getId(), ZonedDateTime.now());

        String url = "/api/exam/courses/" + course.getId() + "/exams/" + testExam.getId() + "/student-exams/" + studentExam.getId() + "/request-feedback";
        request.postWithoutResponseBody(url, null, HttpStatus.OK);

        verify(textFeedbackApi, times(1)).generateAutomaticFeedbackForTestExamAsync(any(StudentParticipation.class), eq(textExercise));
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

        verify(textFeedbackApi, never()).generateAutomaticFeedbackForTestExamAsync(any(), any());
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

        AthenaFeedbackUsageDTO usage = studentExamService.getAthenaFeedbackUsage(student.getId(), testExam.getId());

        assertThat(usage.used()).isZero();
    }
}
