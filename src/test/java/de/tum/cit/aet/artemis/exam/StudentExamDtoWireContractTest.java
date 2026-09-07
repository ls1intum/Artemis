package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.util.ComplaintUtilService;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Wire-contract regression tests for the exam student-exam DTO projections ({@link de.tum.cit.aet.artemis.exam.dto.summary.ExamForSummaryDTO},
 * {@link de.tum.cit.aet.artemis.exam.dto.CourseForStudentExamDTO}, {@link de.tum.cit.aet.artemis.exam.dto.conduction.ResultForConductionDTO}).
 * <p>
 * Pins the exact JSON shape at the fields the exam-taking client (post-publish complaint / example-solution / score-rounding UI) and the
 * instructor student-exam detail screen (complaint column) read, using non-default field values throughout: a prior wire dump that used
 * only default values (null dates, {@code accuracyOfScores == null}) let these fields silently drop out of the DTO projections without
 * failing any assertion.
 */
class StudentExamDtoWireContractTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "examdtowire";

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ComplaintUtilService complaintUtilService;

    private User student;

    private User instructor;

    private Course course;

    private Exam exam;

    private TextExercise textExercise;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        // the enrolling variant is required: the endpoints under test authorize through the course roles of the user, so without enrollment every request is answered with 403
        textExercise = examUtilService.addEnrolledCourseExamWithReviewDatesExerciseGroupWithOneTextExercise(TEST_PREFIX);
        exam = textExercise.getExerciseGroup().getExam();
        course = exam.getCourse();

        // non-default values throughout: a wire dump using defaults (null / accuracyOfScores == null) is exactly how these fields
        // were previously missed, so the test data must never coincide with a default.
        course.setAccuracyOfScores(2);
        // above the client's 2000 fallback: a missing wire value would cap complaints at 2000, not at these limits
        course.setMaxComplaintTextLimit(5000);
        course.setMaxComplaintResponseTextLimit(4000);
        courseRepository.save(course);

        exam.setExampleSolutionPublicationDate(ZonedDateTime.now().minusMinutes(30));
        exam.setPublishResultsDate(ZonedDateTime.now().minusMinutes(15));
        exam = examRepository.save(exam);
    }

    private record SubmittedStudentExamWithResult(StudentExam studentExam, Submission submission) {
    }

    private SubmittedStudentExamWithResult createSubmittedStudentExamWithResult(boolean hasComplaint) {
        StudentExam studentExam = examUtilService.addStudentExamWithUser(exam, student);
        studentExam.setSubmitted(true);
        studentExam.addExercise(textExercise);
        studentExam = studentExamRepository.save(studentExam);

        StudentParticipation participation = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise, student);
        participation = studentParticipationRepository.save(participation);
        Submission submission = ParticipationFactory.generateTextSubmission("Test submission", Language.ENGLISH, true);
        submission.setParticipation(participation);
        participation.addSubmission(submission);
        submission = submissionRepository.save(submission);

        Result result = participationUtilService.generateResultWithScore(submission, instructor, 80.0);
        result.hasComplaint(hasComplaint);
        submission.addResult(result);
        studentParticipationRepository.save(participation);
        submission = submissionRepository.save(submission);

        return new SubmittedStudentExamWithResult(studentExam, submission);
    }

    private JsonNode findFirstResult(JsonNode examOrStudentExamNode) {
        for (JsonNode exercise : examOrStudentExamNode.get("exercises")) {
            JsonNode participations = exercise.get("studentParticipations");
            if (participations == null) {
                continue;
            }
            for (JsonNode participation : participations) {
                JsonNode submissions = participation.get("submissions");
                if (submissions == null) {
                    continue;
                }
                for (JsonNode submission : submissions) {
                    JsonNode results = submission.get("results");
                    if (results != null && !results.isEmpty()) {
                        return results.get(0);
                    }
                }
            }
        }
        return null;
    }

    /**
     * FINDING 1: the summary wire must carry exampleSolutionPublicationDate, both examStudentReview dates and the course's
     * accuracyOfScores; the exam-taking client reads all four after publish to gate the example solution, the complaint /
     * review UI and score rounding.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void summaryWireCarriesExampleSolutionReviewDatesAndAccuracyOfScores() throws Exception {
        StudentExam studentExam = createSubmittedStudentExamWithResult(false).studentExam();

        JsonNode summaryWire = request.get("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/" + studentExam.getId() + "/summary", HttpStatus.OK,
                JsonNode.class);

        JsonNode examNode = summaryWire.get("exam");
        assertThat(examNode).as("summary wire must carry the nested exam").isNotNull();
        assertThat(examNode.hasNonNull("exampleSolutionPublicationDate")).as("exampleSolutionPublicationDate must be on the wire").isTrue();
        assertThat(examNode.hasNonNull("examStudentReviewStart")).as("examStudentReviewStart must be on the wire").isTrue();
        assertThat(examNode.hasNonNull("examStudentReviewEnd")).as("examStudentReviewEnd must be on the wire").isTrue();

        JsonNode courseNode = examNode.get("course");
        assertThat(courseNode).as("summary wire must carry the nested course").isNotNull();
        assertThat(courseNode.path("accuracyOfScores").asInt()).isEqualTo(2);
        assertThat(courseNode.path("maxComplaintTextLimit").asInt()).isEqualTo(5000);
        assertThat(courseNode.path("maxComplaintResponseTextLimit").asInt()).isEqualTo(4000);
    }

    /**
     * FINDING 4: the summary wire must carry the course's {@code athenaFormativeFeedbackEnabled} flag; the test-exam AI
     * feedback button ({@code exam-request-ai-feedback-button.component}) reads {@code exam.course.athenaFormativeFeedbackEnabled}
     * to decide whether to show itself, and with a bare {@code CourseForStudentExamDTO} projection the field was always absent,
     * hiding the button even when formative feedback was enabled for the course.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void summaryWireCarriesAthenaFormativeFeedbackEnabled() throws Exception {
        CourseAthenaConfig athenaConfig = new CourseAthenaConfig();
        athenaConfig.setFormativeFeedbackEnabled(true);
        course.setAthenaConfig(athenaConfig);
        courseRepository.save(course);

        StudentExam studentExam = createSubmittedStudentExamWithResult(false).studentExam();

        JsonNode summaryWire = request.get("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/" + studentExam.getId() + "/summary", HttpStatus.OK,
                JsonNode.class);

        JsonNode courseNode = summaryWire.get("exam").get("course");
        assertThat(courseNode).as("summary wire must carry the nested course").isNotNull();
        assertThat(courseNode.path("athenaFormativeFeedbackEnabled").asBoolean()).as("athenaFormativeFeedbackEnabled must be on the summary wire").isTrue();
    }

    /**
     * FINDING 3: the instructor student-exam detail screen ({@code getStudentExam}, grade DTO path) shares the
     * {@code exam-result-summary} component with the student {@code /summary} path and gates the results / example-solution /
     * complaint-review UI on {@code exam.publishResultsDate}, {@code exam.exampleSolutionPublicationDate} and
     * {@code exam.examStudentReviewStart/End}. The nested {@code studentExam.exam} must therefore carry all four (plus the
     * course's {@code accuracyOfScores}); with a bare conduction exam projection they were absent and the screen always fell
     * back to "results not yet published". Uses a published exam with non-default values, since a wire dump using an
     * unpublished fixture (null publishResultsDate) is exactly how this was missed.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void instructorGetStudentExamWireCarriesPublishGateFieldsAndAccuracyOfScores() throws Exception {
        StudentExam studentExam = createSubmittedStudentExamWithResult(false).studentExam();

        JsonNode gradeWire = request.get("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/" + studentExam.getId(), HttpStatus.OK, JsonNode.class);

        JsonNode studentExamNode = gradeWire.get("studentExam");
        assertThat(studentExamNode).as("grade DTO wire must carry the nested student exam").isNotNull();
        JsonNode examNode = studentExamNode.get("exam");
        assertThat(examNode).as("instructor detail wire must carry the nested exam").isNotNull();
        assertThat(examNode.hasNonNull("publishResultsDate")).as("publishResultsDate must be on the instructor detail wire").isTrue();
        assertThat(examNode.hasNonNull("exampleSolutionPublicationDate")).as("exampleSolutionPublicationDate must be on the instructor detail wire").isTrue();
        assertThat(examNode.hasNonNull("examStudentReviewStart")).as("examStudentReviewStart must be on the instructor detail wire").isTrue();
        assertThat(examNode.hasNonNull("examStudentReviewEnd")).as("examStudentReviewEnd must be on the instructor detail wire").isTrue();

        JsonNode courseNode = examNode.get("course");
        assertThat(courseNode).as("instructor detail wire must carry the nested course").isNotNull();
        assertThat(courseNode.path("accuracyOfScores").asInt()).isEqualTo(2);
        assertThat(courseNode.path("maxComplaintTextLimit").asInt()).isEqualTo(5000);
        assertThat(courseNode.path("maxComplaintResponseTextLimit").asInt()).isEqualTo(4000);
    }

    /**
     * FINDING 2 (student side): the masked student-facing summary wire must keep carrying {@code hasComplaint} exactly as
     * the pre-DTO entity wire did (never stripped by {@code Result#filterSensitiveInformation}), including the explicit
     * {@code false} case rather than silently omitting the field.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void summaryWireCarriesHasComplaintFalseForResultWithoutComplaint() throws Exception {
        StudentExam studentExam = createSubmittedStudentExamWithResult(false).studentExam();

        JsonNode summaryWire = request.get("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/" + studentExam.getId() + "/summary", HttpStatus.OK,
                JsonNode.class);

        JsonNode result = findFirstResult(summaryWire);
        assertThat(result).as("summary wire must carry the result leaf").isNotNull();
        assertThat(result.has("hasComplaint")).as("hasComplaint must be on the wire, matching the pre-DTO masked entity wire").isTrue();
        assertThat(result.path("hasComplaint").asBoolean()).isFalse();
    }

    /**
     * FINDING 2 (instructor side): the instructor student-exam detail screen (grade DTO path, {@code getStudentExam}) must
     * see {@code hasComplaint == true} once a complaint has been filed, so the complaint column renders correctly.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void instructorGetStudentExamWireCarriesHasComplaintTrueForResultWithComplaint() throws Exception {
        SubmittedStudentExamWithResult setup = createSubmittedStudentExamWithResult(true);
        complaintUtilService.addComplaintToSubmission(setup.submission(), student.getLogin(), ComplaintType.COMPLAINT);

        JsonNode gradeWire = request.get("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/" + setup.studentExam().getId(), HttpStatus.OK,
                JsonNode.class);

        JsonNode studentExamNode = gradeWire.get("studentExam");
        assertThat(studentExamNode).as("grade DTO wire must carry the nested student exam").isNotNull();
        JsonNode result = findFirstResult(studentExamNode);
        assertThat(result).as("grade DTO wire must carry the result leaf").isNotNull();
        assertThat(result.path("hasComplaint").asBoolean()).as("hasComplaint must be true once a complaint was filed").isTrue();
    }
}
