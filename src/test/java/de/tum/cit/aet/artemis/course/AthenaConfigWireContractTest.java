package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Pins the endpoints that have to report the two Athena course switches.
 * <p>
 * {@code Course.athenaConfig} is lazy and {@code open-in-view} is disabled, so a response built from a course that
 * nobody resolved the configuration for reports both switches as false - no exception, no log line, just the AI
 * feedback features quietly missing from the client. Each endpoint here is one the webapp reads them from, and each
 * one resolves the configuration explicitly through {@code CourseAthenaConfigRepository}.
 * <p>
 * The assessment editors are covered twice, because a tutor reaches them two ways: by submission id, and by asking for
 * the next unassessed submission. Both hand the exercise to the editor, and the by-id path additionally reloads the
 * submission for the lock, which drops whatever the first query had resolved.
 */
class AthenaConfigWireContractTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "athenawire";

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    private Course course;

    private TextExercise textExercise;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        CourseAthenaConfig athenaConfig = new CourseAthenaConfig();
        athenaConfig.setGradingFeedbackEnabled(true);
        athenaConfig.setFormativeFeedbackEnabled(true);
        course.setAthenaConfig(athenaConfig);
        course = courseRepository.save(course);

        textExercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(3),
                ZonedDateTime.now().plusDays(5));
    }

    private void assertBothFlagsPresent(JsonNode course) {
        assertThat(course).as("the response has to carry the course").isNotNull();
        assertThat(course.get("athenaGradingFeedbackEnabled").asBoolean())
                .as("athenaGradingFeedbackEnabled must reach the client; the assessment views gate feedback suggestions on it").isTrue();
        assertThat(course.get("athenaFormativeFeedbackEnabled").asBoolean()).as("athenaFormativeFeedbackEnabled must reach the client; the request-feedback button gates on it")
                .isTrue();
    }

    private static LinkedMultiValueMap<String, String> lockParams() {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("lock", "true");
        return params;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void exerciseDetailsCarryTheAthenaFlags() throws Exception {
        JsonNode response = request.get("/api/exercise/exercises/" + textExercise.getId() + "/details", HttpStatus.OK, JsonNode.class);
        assertBothFlagsPresent(response.get("exercise").get("course"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void assessmentDashboardExerciseCarriesTheAthenaFlags() throws Exception {
        JsonNode response = request.get("/api/exercise/exercises/" + textExercise.getId() + "/for-assessment-dashboard", HttpStatus.OK, JsonNode.class);
        assertBothFlagsPresent(response.get("course"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void courseManagementDetailCarriesTheAthenaFlags() throws Exception {
        JsonNode response = request.get("/api/course/courses/" + course.getId(), HttpStatus.OK, JsonNode.class);
        assertBothFlagsPresent(response);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void textAssessmentEditorCarriesTheAthenaFlags() throws Exception {
        TextSubmission submission = assessableTextSubmission();
        JsonNode response = request.get("/api/text/text-submissions/" + submission.getId() + "/for-assessment", HttpStatus.OK, JsonNode.class);
        assertBothFlagsPresent(response.get("exercise").get("course"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void nextTextSubmissionToAssessCarriesTheAthenaFlags() throws Exception {
        TextExercise exercise = (TextExercise) assessableTextSubmission().getParticipation().getExercise();
        JsonNode response = request.get("/api/text/exercises/" + exercise.getId() + "/text-submission-without-assessment", HttpStatus.OK, JsonNode.class, lockParams());
        assertBothFlagsPresent(response.get("participation").get("exercise").get("course"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void modelingAssessmentEditorCarriesTheAthenaFlags() throws Exception {
        ModelingSubmission submission = assessableModelingSubmission();
        JsonNode response = request.get("/api/modeling/modeling-submissions/" + submission.getId(), HttpStatus.OK, JsonNode.class);
        assertBothFlagsPresent(response.get("participation").get("exercise").get("course"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void nextModelingSubmissionToAssessCarriesTheAthenaFlags() throws Exception {
        ModelingExercise exercise = (ModelingExercise) assessableModelingSubmission().getParticipation().getExercise();
        JsonNode response = request.get("/api/modeling/exercises/" + exercise.getId() + "/modeling-submission-without-assessment", HttpStatus.OK, JsonNode.class, lockParams());
        assertBothFlagsPresent(response.get("participation").get("exercise").get("course"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void programmingAssessmentEditorCarriesTheAthenaFlags() throws Exception {
        ProgrammingSubmission submission = assessableProgrammingSubmission();
        JsonNode response = request.get("/api/programming/programming-submissions/" + submission.getId() + "/lock", HttpStatus.OK, JsonNode.class);
        assertBothFlagsPresent(response.get("participation").get("exercise").get("course"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void nextProgrammingSubmissionToAssessCarriesTheAthenaFlags() throws Exception {
        ProgrammingExercise exercise = (ProgrammingExercise) assessableProgrammingSubmission().getParticipation().getExercise();
        JsonNode response = request.get("/api/programming/exercises/" + exercise.getId() + "/programming-submission-without-assessment", HttpStatus.OK, JsonNode.class,
                lockParams());
        assertBothFlagsPresent(response.get("participation").get("exercise").get("course"));
    }

    /**
     * A submitted text submission on an exercise a tutor may already assess.
     *
     * @return the saved submission, with its participation and exercise attached
     */
    private TextSubmission assessableTextSubmission() {
        TextExercise exercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(3), ZonedDateTime.now().minusDays(2),
                ZonedDateTime.now().plusDays(1));
        TextSubmission submission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        return textExerciseUtilService.saveTextSubmission(exercise, submission, TEST_PREFIX + "student1");
    }

    /**
     * A submitted modeling submission on an exercise a tutor may already assess.
     *
     * @return the saved submission, with its participation and exercise attached
     */
    private ModelingSubmission assessableModelingSubmission() {
        ModelingExercise exercise = ModelingExerciseFactory.generateModelingExercise(ZonedDateTime.now().minusDays(3), ZonedDateTime.now().minusDays(2),
                ZonedDateTime.now().plusDays(1), DiagramType.ClassDiagram, course);
        exercise = exerciseRepository.save(exercise);
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission("{}", true);
        return modelingExerciseUtilService.addModelingSubmission(exercise, submission, TEST_PREFIX + "student1");
    }

    /**
     * A submitted programming submission on a manually assessed exercise a tutor may already assess.
     *
     * @return the saved submission, with its participation and exercise attached
     */
    private ProgrammingSubmission assessableProgrammingSubmission() {
        // the helper appends to course.exercises, which is lazy on the instance the setup saved
        ProgrammingExercise exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(courseRepository.findByIdWithEagerExercisesElseThrow(course.getId()));
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise = (ProgrammingExercise) exerciseRepository.save(exercise);
        exerciseUtilService.updateExerciseDueDate(exercise.getId(), ZonedDateTime.now().minusDays(2));
        ProgrammingSubmission submission = ParticipationFactory.generateProgrammingSubmission(true);
        return programmingExerciseUtilService.addProgrammingSubmission(exercise, submission, TEST_PREFIX + "student1");
    }
}
