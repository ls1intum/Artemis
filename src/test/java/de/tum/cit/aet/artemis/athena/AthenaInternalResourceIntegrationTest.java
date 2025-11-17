package de.tum.cit.aet.artemis.athena;

import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_PROGRAMMING_TEST;
import static org.assertj.core.api.Assertions.assertThat;

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
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

class AthenaInternalResourceIntegrationTest extends AbstractAthenaTest {

    private static final String TEST_PREFIX = "athenainternalintegration";

    @Value("${artemis.athena.secret}")
    private String athenaSecret;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

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

        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);

        // Seed a LocalVC bare repository with content
        var sourceRepo = new LocalRepository(defaultBranch);
        sourceRepo.configureRepos(localVCBasePath, "athenaInternalSrcLocalRepo", "athenaInternalSrcOriginRepo");

        // Ensure tests repository URI exists on the exercise
        var testsSlug = programmingExercise.getProjectKey().toLowerCase() + "-tests";
        var testsUri = new LocalVCRepositoryUri(localVCBaseUri, programmingExercise.getProjectKey(), testsSlug);
        programmingExercise.setTestRepositoryUri(testsUri.toString());
        programmingExerciseRepository.save(programmingExercise);

        var sourceUri = new LocalVCRepositoryUri(localVCBaseUri, sourceRepo.remoteBareGitRepoFile.toPath());

        // Copy source repo contents to target (template, solution, tests)
        var templateUri = new LocalVCRepositoryUri(programmingExercise.getTemplateRepositoryUri());
        gitService.copyBareRepositoryWithoutHistory(sourceUri, templateUri, defaultBranch);

        var solutionUri = new LocalVCRepositoryUri(programmingExercise.getSolutionRepositoryUri());
        gitService.copyBareRepositoryWithoutHistory(sourceUri, solutionUri, defaultBranch);

        var testsRepoUri = new LocalVCRepositoryUri(programmingExercise.getTestRepositoryUri());
        gitService.copyBareRepositoryWithoutHistory(sourceUri, testsRepoUri, defaultBranch);

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

        // Enable Athena for the exercise
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        programmingExerciseRepository.save(programmingExercise);

        // Expect status 403 because the Authorization header is wrong
        request.get("/api/athena/internal/programming-exercises/" + programmingExercise.getId() + "/" + urlSuffix, HttpStatus.FORBIDDEN, Result.class, authHeaders);
    }
}
