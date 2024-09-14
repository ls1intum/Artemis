package de.tum.cit.aet.artemis.athena;

import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_MODELING_TEST;
import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_PROGRAMMING_TEST;
import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_TEXT_TEST;
import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_RESTRICTED_MODULE_MODELING_TEST;
import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_RESTRICTED_MODULE_PROGRAMMING_TEST;
import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_RESTRICTED_MODULE_TEXT_TEST;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.modeling.repository.ModelingSubmissionRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.repository.TextSubmissionRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class AthenaResourceIntegrationTest extends AbstractAthenaTest {

    private static final String TEST_PREFIX = "athenaintegration";

    @Value("${artemis.athena.secret}")
    private String athenaSecret;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private ModelingSubmissionRepository modelingSubmissionRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    private TextExercise textExercise;

    private TextSubmission textSubmission;

    private ProgrammingExercise programmingExercise;

    private ProgrammingSubmission programmingSubmission;

    private ModelingExercise modelingExercise;

    private ModelingSubmission modelingSubmission;

    @BeforeEach
    @Override
    protected void initTestCase() {
        super.initTestCase();
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 0);

        var textCourse = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        textExercise = exerciseUtilService.findTextExerciseWithTitle(textCourse.getExercises(), "Text");
        textSubmission = ParticipationFactory.generateTextSubmission("This is a test sentence. This is a second test sentence. This is a third test sentence.", Language.ENGLISH,
                true);
        var studentParticipation = ParticipationFactory.generateStudentParticipation(InitializationState.FINISHED, textExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        studentParticipationRepository.save(studentParticipation);
        textSubmission.setParticipation(studentParticipation);
        textSubmissionRepository.save(textSubmission);

        var programmingCourse = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = exerciseUtilService.findProgrammingExerciseWithTitle(programmingCourse.getExercises(), "Programming");
        // Allow manual results
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(1));
        programmingExerciseRepository.save(programmingExercise);

        programmingSubmission = ParticipationFactory.generateProgrammingSubmission(true);
        var programmingParticipation = ParticipationFactory.generateStudentParticipation(InitializationState.FINISHED, programmingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        studentParticipationRepository.save(programmingParticipation);
        programmingSubmission.setParticipation(programmingParticipation);
        programmingSubmissionRepository.save(programmingSubmission);

        var modelingCourse = modelingExerciseUtilService.addCourseWithOneModelingExercise();
        modelingExercise = exerciseUtilService.findModelingExerciseWithTitle(modelingCourse.getExercises(), "ClassDiagram");
        modelingSubmission = ParticipationFactory.generateModelingSubmission("", true);
        var modelingParticipation = ParticipationFactory.generateStudentParticipation(InitializationState.FINISHED, modelingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        studentParticipationRepository.save(modelingParticipation);
        modelingSubmission.setParticipation(modelingParticipation);
        modelingSubmissionRepository.save(modelingSubmission);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetAvailableProgrammingModulesSuccess_EmptyModules() throws Exception {
        var course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();

        athenaRequestMockProvider.mockGetAvailableModulesSuccessEmptyModulesList();
        List<String> response = request.getList("/api/athena/courses/" + course.getId() + "/programming-exercises/available-modules", HttpStatus.OK, String.class);
        assertThat(response).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetAvailableTextModulesSuccess() throws Exception {
        var course = textExercise.getCourseViaExerciseGroupOrCourseMember();

        athenaRequestMockProvider.mockGetAvailableModulesSuccess();
        List<String> response = request.getList("/api/athena/courses/" + course.getId() + "/text-exercises/available-modules", HttpStatus.OK, String.class);
        assertThat(response).contains(ATHENA_MODULE_TEXT_TEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetAvailableProgrammingModulesSuccess() throws Exception {
        var course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();

        athenaRequestMockProvider.mockGetAvailableModulesSuccess();
        List<String> response = request.getList("/api/athena/courses/" + course.getId() + "/programming-exercises/available-modules", HttpStatus.OK, String.class);
        assertThat(response).contains(ATHENA_MODULE_PROGRAMMING_TEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetAvailableModelingModulesSuccess() throws Exception {
        var course = modelingExercise.getCourseViaExerciseGroupOrCourseMember();

        athenaRequestMockProvider.mockGetAvailableModulesSuccess();
        List<String> response = request.getList("/api/athena/courses/" + course.getId() + "/modeling-exercises/available-modules", HttpStatus.OK, String.class);
        assertThat(response).contains(ATHENA_MODULE_MODELING_TEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetAvailableTextModulesSuccess_RestrictedModuleAccess() throws Exception {
        // give the course access to the restricted Athena modules
        var course = textExercise.getCourseViaExerciseGroupOrCourseMember();
        course.setRestrictedAthenaModulesAccess(true);
        courseRepository.save(course);

        athenaRequestMockProvider.mockGetAvailableModulesSuccess();
        List<String> response = request.getList("/api/athena/courses/" + course.getId() + "/text-exercises/available-modules", HttpStatus.OK, String.class);
        assertThat(response).containsExactlyInAnyOrder(ATHENA_MODULE_TEXT_TEST, ATHENA_RESTRICTED_MODULE_TEXT_TEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetAvailableProgrammingModulesSuccess_RestrictedModuleAccess() throws Exception {
        // give the course access to the restricted Athena modules
        var course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
        course.setRestrictedAthenaModulesAccess(true);
        courseRepository.save(course);

        athenaRequestMockProvider.mockGetAvailableModulesSuccess();
        List<String> response = request.getList("/api/athena/courses/" + course.getId() + "/programming-exercises/available-modules", HttpStatus.OK, String.class);
        assertThat(response).containsExactlyInAnyOrder(ATHENA_MODULE_PROGRAMMING_TEST, ATHENA_RESTRICTED_MODULE_PROGRAMMING_TEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetAvailableModelingModulesSuccess_RestrictedModuleAccess() throws Exception {
        // give the course access to the restricted Athena modules
        var course = modelingExercise.getCourseViaExerciseGroupOrCourseMember();
        course.setRestrictedAthenaModulesAccess(true);
        courseRepository.save(course);

        athenaRequestMockProvider.mockGetAvailableModulesSuccess();
        List<String> response = request.getList("/api/athena/courses/" + course.getId() + "/modeling-exercises/available-modules", HttpStatus.OK, String.class);
        assertThat(response).containsExactlyInAnyOrder(ATHENA_MODULE_MODELING_TEST, ATHENA_RESTRICTED_MODULE_MODELING_TEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetAvailableTextModulesAccessForbidden() throws Exception {
        var course = textExercise.getCourseViaExerciseGroupOrCourseMember();

        athenaRequestMockProvider.mockGetAvailableModulesSuccess();
        request.getList("/api/athena/courses/" + course.getId() + "/text-exercises/available-modules", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetAvailableProgrammingModulesAccessForbidden() throws Exception {
        var course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();

        athenaRequestMockProvider.mockGetAvailableModulesSuccess();
        request.getList("/api/athena/courses/" + course.getId() + "/programming-exercises/available-modules", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetAvailableModelingModulesAccessForbidden() throws Exception {
        var course = modelingExercise.getCourseViaExerciseGroupOrCourseMember();

        athenaRequestMockProvider.mockGetAvailableModulesSuccess();
        request.getList("/api/athena/courses/" + course.getId() + "/modeling-exercises/available-modules", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFeedbackSuggestionsSuccessText() throws Exception {
        // Enable Athena for the exercise
        textExercise.setFeedbackSuggestionModule(ATHENA_MODULE_TEXT_TEST);
        textExerciseRepository.save(textExercise);

        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("text");
        List<Feedback> response = request.getList("/api/athena/text-exercises/" + textExercise.getId() + "/submissions/" + textSubmission.getId() + "/feedback-suggestions",
                HttpStatus.OK, Feedback.class);
        assertThat(response).as("response is not empty").isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFeedbackSuggestionsSuccessProgramming() throws Exception {
        // Enable Athena for the exercise
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        programmingExerciseRepository.save(programmingExercise);

        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("programming");
        List<Feedback> response = request.getList(
                "/api/athena/programming-exercises/" + programmingExercise.getId() + "/submissions/" + programmingSubmission.getId() + "/feedback-suggestions", HttpStatus.OK,
                Feedback.class);
        assertThat(response).as("response is not empty").isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFeedbackSuggestionsSuccessModeling() throws Exception {
        // Enable Athena for the exercise
        modelingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_MODELING_TEST);
        modelingExerciseRepository.save(modelingExercise);

        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("modeling");
        List<Feedback> response = request.getList(
                "/api/athena/modeling-exercises/" + modelingExercise.getId() + "/submissions/" + modelingSubmission.getId() + "/feedback-suggestions", HttpStatus.OK,
                Feedback.class);
        assertThat(response).as("response is not empty").isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetTextFeedbackSuggestionsNotFound() throws Exception {
        request.get("/api/athena/text-exercises/9999/submissions/9999/feedback-suggestions", HttpStatus.NOT_FOUND, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetProgrammingFeedbackSuggestionsNotFound() throws Exception {
        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("programming");
        request.get("/api/athena/programming-exercises/9999/submissions/9999/feedback-suggestions", HttpStatus.NOT_FOUND, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetModelingFeedbackSuggestionsNotFound() throws Exception {
        request.get("/api/athena/modeling-exercises/9999/submissions/9999/feedback-suggestions", HttpStatus.NOT_FOUND, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testGetTextFeedbackSuggestionsAccessForbidden() throws Exception {
        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("text");
        request.get("/api/athena/text-exercises/" + textExercise.getId() + "/submissions/" + textSubmission.getId() + "/feedback-suggestions", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testGetProgrammingFeedbackSuggestionsAccessForbidden() throws Exception {
        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("programming");
        request.get("/api/athena/programming-exercises/" + textExercise.getId() + "/submissions/" + programmingSubmission.getId() + "/feedback-suggestions", HttpStatus.FORBIDDEN,
                List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testGetModelingFeedbackSuggestionsAccessForbidden() throws Exception {
        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("modeling");
        request.get("/api/athena/modeling-exercises/" + textExercise.getId() + "/submissions/" + modelingSubmission.getId() + "/feedback-suggestions", HttpStatus.FORBIDDEN,
                List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFeedbackSuggestionsAthenaEnabled() throws Exception {
        // Create example participation with submission
        var participation = new StudentParticipation();
        participation.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        participation.setExercise(programmingExercise);
        participation = studentParticipationRepository.save(participation);
        programmingSubmission.setParticipation(participation);
        programmingSubmissionRepository.save(programmingSubmission);
        // Create example result
        var result = new Result();
        result.setScore(1.0);
        result.setSubmission(programmingSubmission);
        result.setParticipation(participation);
        result.setAssessmentType(AssessmentType.MANUAL);
        // Create example feedback so that Athena can process it
        var feedback = new Feedback();
        feedback.setCredits(1.0);
        feedback.setResult(result);
        result.setFeedbacks(List.of(feedback));

        result = resultRepository.save(result);
        feedbackRepository.save(feedback);

        // Enable Athena for the exercise
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        programmingExerciseRepository.save(programmingExercise);

        Result response = request.putWithResponseBody("/api/participations/" + participation.getId() + "/manual-results?submit=true", result, Result.class, HttpStatus.OK);

        // Check that nothing went wrong, even with Athena enabled
        assertThat(response).as("response is not null").isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "repository/template", "repository/solution", "repository/tests" })
    void testRepositoryExportEndpoint(String urlSuffix) throws Exception {
        // Enable Athena for the exercise
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        programmingExerciseRepository.save(programmingExercise);

        // Add Git repo for export
        programmingExerciseUtilService.createGitRepository();

        // Get exports from endpoint
        var authHeaders = new HttpHeaders();
        authHeaders.add("Authorization", athenaSecret);
        var repoZip = request.getFile("/api/public/athena/programming-exercises/" + programmingExercise.getId() + "/" + urlSuffix, HttpStatus.OK, new LinkedMultiValueMap<>(),
                authHeaders, null);

        // Check that ZIP contains file
        try (var zipFile = new ZipFile(repoZip)) {
            assertThat(zipFile.size()).as("zip file contains files").isGreaterThan(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "repository/template", "repository/solution", "repository/tests", "submissions/100/repository" })
    void testRepositoryExportEndpointsFailWhenAthenaNotEnabled(String urlSuffix) throws Exception {
        var authHeaders = new HttpHeaders();
        authHeaders.add("Authorization", athenaSecret);

        // Expect status 503 because Athena is not enabled for the exercise
        request.get("/api/public/athena/programming-exercises/" + programmingExercise.getId() + "/" + urlSuffix, HttpStatus.SERVICE_UNAVAILABLE, Result.class, authHeaders);
    }

    @ParameterizedTest
    @ValueSource(strings = { "repository/template", "repository/solution", "repository/tests", "submissions/100/repository" })
    void testRepositoryExportEndpointsFailWithWrongAuthentication(String urlSuffix) throws Exception {
        var authHeaders = new HttpHeaders();
        authHeaders.add("Authorization", athenaSecret + "-wrong");

        // Enable Athena for the exercise
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        programmingExerciseRepository.save(programmingExercise);

        // Expect status 403 because the Authorization header is wrong
        request.get("/api/public/athena/programming-exercises/" + programmingExercise.getId() + "/" + urlSuffix, HttpStatus.FORBIDDEN, Result.class, authHeaders);
    }
}
