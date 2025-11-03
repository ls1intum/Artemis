package de.tum.cit.aet.artemis.athena;

import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_PROGRAMMING_TEST;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.zip.ZipFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

class AthenaInternalResourceIntegrationTest extends AbstractAthenaTest {

    private static final String TEST_PREFIX = "athenainternalintegration";

    @Value("${artemis.athena.secret}")
    private String athenaSecret;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    @Override
    protected void initTestCase() {
        super.initTestCase();
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 0);

        var programmingCourse = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(programmingCourse, ProgrammingExercise.class);
        // Allow manual results
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExerciseRepository.save(programmingExercise);
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
        authHeaders.add(HttpHeaders.AUTHORIZATION, athenaSecret);
        var repoZip = request.getFile("/api/athena/internal/programming-exercises/" + programmingExercise.getId() + "/" + urlSuffix, HttpStatus.OK, new LinkedMultiValueMap<>(),
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
        authHeaders.add(HttpHeaders.AUTHORIZATION, athenaSecret);

        // Expect status 503 because Athena is not enabled for the exercise
        request.get("/api/athena/internal/programming-exercises/" + programmingExercise.getId() + "/" + urlSuffix, HttpStatus.SERVICE_UNAVAILABLE, Result.class, authHeaders);
    }

    @ParameterizedTest
    @ValueSource(strings = { "repository/template", "repository/solution", "repository/tests", "submissions/100/repository" })
    void testRepositoryExportEndpointsFailWithWrongAuthentication(String urlSuffix) throws Exception {
        var authHeaders = new HttpHeaders();
        authHeaders.add(HttpHeaders.AUTHORIZATION, athenaSecret + "-wrong");

        // Enable Athena for the exercise
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        programmingExerciseRepository.save(programmingExercise);

        // Expect status 403 because the Authorization header is wrong
        request.get("/api/athena/internal/programming-exercises/" + programmingExercise.getId() + "/" + urlSuffix, HttpStatus.FORBIDDEN, Result.class, authHeaders);
    }
}
