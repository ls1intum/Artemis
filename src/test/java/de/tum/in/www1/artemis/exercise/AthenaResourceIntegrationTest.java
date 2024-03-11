package de.tum.in.www1.artemis.exercise;

import static de.tum.in.www1.artemis.connector.AthenaRequestMockProvider.*;
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

import de.tum.in.www1.artemis.AbstractAthenaTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.user.UserUtilService;

class AthenaResourceIntegrationTest extends AbstractAthenaTest {

    private static final String TEST_PREFIX = "athenaintegration";

    @Value("${artemis.athena.secret}")
    private String athenaSecret;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private CourseRepository courseRepository;

    private TextExercise textExercise;

    private TextSubmission textSubmission;

    private ProgrammingExercise programmingExercise;

    private ProgrammingSubmission programmingSubmission;

    @BeforeEach
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
    void testGetAvailableProgrammingModulesSuccess() throws Exception {
        var course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();

        athenaRequestMockProvider.mockGetAvailableModulesSuccess();
        List<String> response = request.getList("/api/athena/courses/" + course.getId() + "/programming-exercises/available-modules", HttpStatus.OK, String.class);
        assertThat(response).contains(ATHENA_MODULE_PROGRAMMING_TEST);
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
    void testGetAvailableTextModulesSuccess() throws Exception {
        var course = textExercise.getCourseViaExerciseGroupOrCourseMember();

        athenaRequestMockProvider.mockGetAvailableModulesSuccess();
        List<String> response = request.getList("/api/athena/courses/" + course.getId() + "/text-exercises/available-modules", HttpStatus.OK, String.class);
        assertThat(response).contains(ATHENA_MODULE_TEXT_TEST);
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
    void testGetAvailableTextModulesAccessForbidden() throws Exception {
        var course = textExercise.getCourseViaExerciseGroupOrCourseMember();

        athenaRequestMockProvider.mockGetAvailableModulesSuccess();
        request.getList("/api/athena/courses/" + course.getId() + "/text-exercises/available-modules", HttpStatus.FORBIDDEN, String.class);
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
