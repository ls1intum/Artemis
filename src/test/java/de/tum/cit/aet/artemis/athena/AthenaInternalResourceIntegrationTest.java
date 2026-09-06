package de.tum.cit.aet.artemis.athena;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.type.TypeReference;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.util.LocalVCRepositoryTestService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

class AthenaInternalResourceIntegrationTest extends AbstractAthenaTest {

    private static final String TEST_PREFIX = "athenainternalintegration";

    @Value("${artemis.athena.secret}")
    private String athenaSecret;

    @Autowired
    private CourseTestRepository courseTestRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private LocalVCRepositoryTestService localVCRepositoryTestService;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    @Override
    protected void initTestCase() {
        super.initTestCase();
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 0);

        var programmingCourse = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(programmingCourse, ProgrammingExercise.class);
        // Allow manual results
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExerciseRepository.save(programmingExercise);
    }

    @ParameterizedTest
    @ValueSource(strings = { "repository/template", "repository/solution", "repository/tests" })
    void testRepositoryExportEndpoint(String urlSuffix) throws Exception {
        // Enable Athena grading feedback at course level
        var course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
        var athenaConfig = new CourseAthenaConfig();
        athenaConfig.setCourse(course);
        athenaConfig.setGradingFeedbackEnabled(true);
        course.setAthenaConfig(athenaConfig);
        courseTestRepository.save(course);

        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);

        // Write the expected file into the real template, solution and tests repositories of the exercise.
        for (String repositoryUri : List.of(programmingExercise.getTemplateRepositoryUri(), programmingExercise.getSolutionRepositoryUri(),
                programmingExercise.getTestRepositoryUri())) {
            localVCRepositoryTestService.writeFilesAndPush(new LocalVCRepositoryUri(repositoryUri), Map.of("README.md", "Initial commit"), "Initial commit");
        }

        // Get repository contents as map from endpoint
        var authHeaders = new HttpHeaders();
        authHeaders.add(HttpHeaders.AUTHORIZATION, athenaSecret);

        String json = request.get("/api/athena/internal/programming-exercises/" + programmingExercise.getId() + "/" + urlSuffix, HttpStatus.OK, String.class, authHeaders);
        Map<String, String> repoFiles = request.getObjectMapper().readValue(json, new TypeReference<Map<String, String>>() {
        });
        assertThat(repoFiles).as("export returns exactly one file: README.md").isNotNull().hasSize(1).containsOnlyKeys("README.md").containsEntry("README.md", "Initial commit");
    }

    @ParameterizedTest
    @ValueSource(strings = { "repository/template", "repository/solution", "repository/tests", "submissions/100/repository" })
    void testRepositoryExportEndpointsFailWhenAthenaNotEnabled(String urlSuffix) throws Exception {
        var authHeaders = new HttpHeaders();
        authHeaders.add(HttpHeaders.AUTHORIZATION, athenaSecret);

        // Expect status 503 because Athena is not enabled for the exercise
        request.get("/api/athena/internal/programming-exercises/" + programmingExercise.getId() + "/" + urlSuffix, HttpStatus.SERVICE_UNAVAILABLE, Result.class, authHeaders);
    }

    @ParameterizedTest
    @ValueSource(strings = { "repository/template", "repository/solution", "repository/tests", "submissions/100/repository" })
    void testRepositoryExportEndpointsFailWithWrongAuthentication(String urlSuffix) throws Exception {
        var authHeaders = new HttpHeaders();
        authHeaders.add(HttpHeaders.AUTHORIZATION, athenaSecret + "-wrong");

        // Expect status 403 because the Authorization header is wrong
        request.get("/api/athena/internal/programming-exercises/" + programmingExercise.getId() + "/" + urlSuffix, HttpStatus.FORBIDDEN, Result.class, authHeaders);
    }
}
