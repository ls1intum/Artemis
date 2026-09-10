package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadAssessmentInputDTO;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Verifies the single gate that stops tutors from assessing exam exercises before every student has finished the exam.
 * <p>
 * Before this gate existed only programming exercises were (indirectly) protected, so text, modeling and file upload
 * submissions could be assessed while students were still working on them, see issue #13358.
 */
class ExamAssessmentAvailabilityIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "examassessavail";

    private static final String ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING = "assessmentNotPossibleExamRunning";

    private static final String ASSESSMENT_NOT_POSSIBLE_TESTS_PENDING = "assessmentNotPossibleTestsPending";

    /** The exercise types a tutor assesses manually and that therefore share the gate. */
    private static final List<Class<? extends Exercise>> MANUALLY_ASSESSED_TYPES = List.of(TextExercise.class, ModelingExercise.class, FileUploadExercise.class,
            ProgrammingExercise.class);

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExamTestRepository examTestRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private Exam exam;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        // addUsers clears every user_course_role, so the course has to be one that enrolls this test's users again:
        // course access is a role now, and an empty course would leave the tutor without one (plain 403, no gate).
        Course course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        exam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndProgramming(course);
        // the exam is still running: it started an hour ago and the last student can hand in in another hour
        setExamWorkingPeriod(ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusHours(1));
    }

    private void setExamWorkingPeriod(ZonedDateTime startDate, ZonedDateTime endDate) {
        exam.setVisibleDate(startDate.minusHours(1));
        exam.setStartDate(startDate);
        exam.setEndDate(endDate);
        exam.setWorkingTime(Math.toIntExact(ChronoUnit.SECONDS.between(startDate, endDate)));
        exam.setGracePeriod(180);
        exam = examTestRepository.save(exam);
    }

    private <E extends Exercise> E exerciseOfType(Class<E> exerciseType) {
        return exam.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream()).filter(exerciseType::isInstance).map(exerciseType::cast).findFirst().orElseThrow();
    }

    private Submission saveSubmission(Exercise exercise) {
        return saveSubmission(exercise, TEST_PREFIX + "student1");
    }

    private Submission saveSubmission(Exercise exercise, String login) {
        Submission submission = switch (exercise) {
            case TextExercise ignored -> ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
            case ModelingExercise ignored -> ParticipationFactory.generateModelingSubmission("{}", true);
            case FileUploadExercise ignored -> ParticipationFactory.generateFileUploadSubmission(true);
            case ProgrammingExercise ignored -> ParticipationFactory.generateProgrammingSubmission(true);
            default -> throw new IllegalArgumentException("Unsupported exercise type " + exercise.getClass());
        };
        return participationUtilService.addSubmission(exercise, submission, login);
    }

    /**
     * @return the URL a tutor opens to assess the given submission, which is where the gate has to apply
     */
    private String assessmentUrl(Exercise exercise, Submission submission) {
        return switch (exercise) {
            case TextExercise ignored -> "/api/text/text-submissions/" + submission.getId() + "/for-assessment";
            case ModelingExercise ignored -> "/api/modeling/modeling-submissions/" + submission.getId();
            case FileUploadExercise ignored -> "/api/fileupload/file-upload-submissions/" + submission.getId();
            case ProgrammingExercise ignored -> "/api/programming/programming-submissions/" + submission.getId() + "/lock";
            default -> throw new IllegalArgumentException("Unsupported exercise type " + exercise.getClass());
        };
    }

    private String getForbiddenResponseBody(String url) throws Exception {
        return request.performMvcRequest(get(url)).andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testOpeningAnAssessmentIsForbiddenWhileTheExamIsRunningForEveryExerciseType() throws Exception {
        for (Class<? extends Exercise> exerciseType : MANUALLY_ASSESSED_TYPES) {
            Exercise exercise = exerciseOfType(exerciseType);
            Submission submission = saveSubmission(exercise);

            String response = getForbiddenResponseBody(assessmentUrl(exercise, submission));

            assertThat(response).as("assessing a %s while the exam runs must be rejected with the explaining error key", exerciseType.getSimpleName())
                    .contains(ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTheErrorTellsTheClientTheDateAndToRenderTheMessageItself() throws Exception {
        TextExercise textExercise = exerciseOfType(TextExercise.class);
        Submission submission = saveSubmission(textExercise);

        String response = getForbiddenResponseBody(assessmentUrl(textExercise, submission));

        // the client localizes this date and shows the tutor when they can start
        assertThat(response).contains("\"date\":");
        assertThat(response).contains("\"message\":\"error." + ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING + "\"");
        // the assessment page renders the alert with a localized date, so the generic interceptor has to stay silent
        assertThat(response).contains("\"skipAlert\":true");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAssessmentIsPossibleOnceTheExamAndItsGracePeriodAreOver() throws Exception {
        setExamWorkingPeriod(ZonedDateTime.now().minusHours(3), ZonedDateTime.now().minusHours(2));

        for (Class<? extends Exercise> exerciseType : List.of(TextExercise.class, ModelingExercise.class, FileUploadExercise.class)) {
            Exercise exercise = exerciseOfType(exerciseType);
            Submission submission = saveSubmission(exercise);

            request.performMvcRequest(get(assessmentUrl(exercise, submission))).andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTheGracePeriodStillCountsAsExamTime() throws Exception {
        // the last student exam ended a minute ago, but students may still hand in during the grace period
        setExamWorkingPeriod(ZonedDateTime.now().minusHours(1), ZonedDateTime.now().minusMinutes(1));
        TextExercise textExercise = exerciseOfType(TextExercise.class);
        Submission submission = saveSubmission(textExercise);

        String response = getForbiddenResponseBody(assessmentUrl(textExercise, submission));

        assertThat(response).contains(ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testProgrammingAssessmentAdditionallyWaitsForTheTestsToRunOnTheFinalSubmissions() throws Exception {
        setExamWorkingPeriod(ZonedDateTime.now().minusHours(3), ZonedDateTime.now().minusHours(2));
        ProgrammingExercise programmingExercise = exerciseOfType(ProgrammingExercise.class);
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusMinutes(15));
        exerciseRepository.save(programmingExercise);
        Submission submission = saveSubmission(programmingExercise);

        String response = getForbiddenResponseBody(assessmentUrl(programmingExercise, submission));

        assertThat(response).contains(ASSESSMENT_NOT_POSSIBLE_TESTS_PENDING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testTestRunsStayAssessableBeforeTheExamStarts() throws Exception {
        setExamWorkingPeriod(ZonedDateTime.now().plusHours(1), ZonedDateTime.now().plusHours(2));

        for (Class<? extends Exercise> exerciseType : List.of(TextExercise.class, ModelingExercise.class, FileUploadExercise.class)) {
            Exercise exercise = exerciseOfType(exerciseType);
            // a test run participation belongs to the instructor who created it, not to a student
            Submission submission = saveSubmission(exercise, TEST_PREFIX + "instructor1");
            StudentParticipation participation = (StudentParticipation) submission.getParticipation();
            participation.setTestRun(true);
            studentParticipationRepository.save(participation);

            request.performMvcRequest(get(assessmentUrl(exercise, submission))).andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreatingAnAssessmentIsForbiddenWhileTheExamIsRunningEvenForInstructors() throws Exception {
        // This endpoint creates a result when none exists yet, and isAllowedToCreateOrOverrideResult neither includes
        // the grace period nor applies to instructors at all. Without the gate on the write side, assessment data could
        // therefore be persisted while the student can still change their submission.
        FileUploadExercise fileUploadExercise = exerciseOfType(FileUploadExercise.class);
        Submission submission = saveSubmission(fileUploadExercise);
        var assessment = new FileUploadAssessmentInputDTO(List.of(), null);

        String response = request
                .performMvcRequest(put("/api/fileupload/file-upload-submissions/" + submission.getId() + "/feedback").param("submit", "true")
                        .contentType(MediaType.APPLICATION_JSON).content(new JsonMapper().writeValueAsString(assessment)))
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();

        assertThat(response).contains(ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING);
        assertThat(resultRepository.findDistinctBySubmissionId(submission.getId())).as("no result may be persisted while the exam is still running").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTheAssessmentDashboardTellsTheClientWhenAssessmentBecomesPossible() throws Exception {
        TextExercise textExercise = exerciseOfType(TextExercise.class);

        var exerciseForDashboard = request.get("/api/exercise/exercises/" + textExercise.getId() + "/for-assessment-dashboard", HttpStatus.OK, TextExercise.class);

        ZonedDateTime expectedLatestExamEndDate = exam.getEndDate().plusSeconds(exam.getGracePeriod());
        assertThat(exerciseForDashboard.getLatestExamEndDate()).isCloseTo(expectedLatestExamEndDate, within(1, ChronoUnit.SECONDS));
        // a text exercise has no build to wait for, so both dates are the same
        assertThat(exerciseForDashboard.getAssessmentPossibleFrom()).isCloseTo(expectedLatestExamEndDate, within(1, ChronoUnit.SECONDS));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTheAssessmentDashboardSendsNoSuchDatesForCourseExercises() throws Exception {
        Course course = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        TextExercise textExercise = ExerciseUtilService.findTextExerciseWithTitle(course.getExercises(), "Text");
        textExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exerciseRepository.save(textExercise);

        var exerciseForDashboard = request.get("/api/exercise/exercises/" + textExercise.getId() + "/for-assessment-dashboard", HttpStatus.OK, TextExercise.class);

        assertThat(exerciseForDashboard.getLatestExamEndDate()).isNull();
        assertThat(exerciseForDashboard.getAssessmentPossibleFrom()).isNull();
    }
}
