package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.config.Constants.*;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.SOLUTION;
import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.TEMPLATE;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import de.tum.in.www1.artemis.AbstractSpringIntegrationTest;
import de.tum.in.www1.artemis.connector.bamboo.BambooRequestMockProvider;
import de.tum.in.www1.artemis.connector.bitbucket.BitbucketRequestMockProvider;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.ProgrammingExerciseImportService;
import de.tum.in.www1.artemis.service.ProgrammingExerciseService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.util.Verifiable;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

public class ProgrammingExerciseServiceIntegrationTest extends AbstractSpringIntegrationTest {

    private static final String BASE_RESOURCE = "/api/programming-exercises/";

    @Value("${server.url}")
    protected String ARTEMIS_SERVER_URL;

    @Autowired
    ProgrammingExerciseService programmingExerciseService;

    @Autowired
    ProgrammingExerciseImportService programmingExerciseImportService;

    @Autowired
    DatabaseUtilService databse;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    RequestUtilService request;

    @Autowired
    private BambooRequestMockProvider bambooRequestMockProvider;

    @Autowired
    private BitbucketRequestMockProvider bitbucketRequestMockProvider;

    private Course additionalEmptyCourse;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    public void setUp() {
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();
        databse.addUsers(1, 1, 1);
        databse.addInstructor("other-instructors", "instructorother");
        databse.addCourseWithOneProgrammingExerciseAndTestCases();
        additionalEmptyCourse = databse.addEmptyCourse();
        programmingExercise = databse.loadProgrammingExerciseWithEagerReferences();
        databse.addHintsToExercise(programmingExercise);
        databse.addHintsToProblemStatement(programmingExercise);

        // Load again to fetch changes to statement and hints while keeping eager refs
        programmingExercise = databse.loadProgrammingExerciseWithEagerReferences();
    }

    @AfterEach
    public void tearDown() {
        databse.resetDatabase();
    }

    @Test
    public void importProgrammingExerciseBasis_baseReferencesGotCloned() throws MalformedURLException {
        final var newlyImported = importExerciseBase();

        assertThat(newlyImported.getId()).isNotEqualTo(programmingExercise.getId());
        assertThat(newlyImported != programmingExercise).isTrue();
        assertThat(newlyImported.getTemplateParticipation().getId()).isNotEqualTo(programmingExercise.getTemplateParticipation().getId());
        assertThat(newlyImported.getSolutionParticipation().getId()).isNotEqualTo(programmingExercise.getSolutionParticipation().getId());
        assertThat(newlyImported.getProgrammingLanguage()).isEqualTo(programmingExercise.getProgrammingLanguage());
        assertThat(newlyImported.getProjectKey()).isNotEqualTo(programmingExercise.getProjectKey());
        assertThat(newlyImported.getSolutionBuildPlanId()).isNotEqualTo(programmingExercise.getSolutionBuildPlanId());
        assertThat(newlyImported.getTemplateBuildPlanId()).isNotEqualTo(programmingExercise.getTemplateBuildPlanId());
        assertThat(newlyImported.hasSequentialTestRuns()).isEqualTo(programmingExercise.hasSequentialTestRuns());
        assertThat(newlyImported.isAllowOnlineEditor()).isEqualTo(programmingExercise.isAllowOnlineEditor());
        assertThat(newlyImported.getNumberOfAssessments()).isNull();
        assertThat(newlyImported.getNumberOfComplaints()).isNull();
        assertThat(newlyImported.getNumberOfMoreFeedbackRequests()).isNull();
        assertThat(newlyImported.getNumberOfParticipations()).isNull();
        assertThat(newlyImported.getAttachments()).isNull();
        assertThat(newlyImported.getTutorParticipations()).isNull();
        assertThat(newlyImported.getExampleSubmissions()).isNull();
        assertThat(newlyImported.getStudentQuestions()).isNull();
        assertThat(newlyImported.getStudentParticipations()).isNull();
        final var newTestCaseIDs = newlyImported.getTestCases().stream().map(ProgrammingExerciseTestCase::getId).collect(Collectors.toSet());
        assertThat(newlyImported.getTestCases().size()).isEqualTo(programmingExercise.getTestCases().size());
        assertThat(programmingExercise.getTestCases()).noneMatch(testCase -> newTestCaseIDs.contains(testCase.getId()));
        final var newHintIDs = newlyImported.getExerciseHints().stream().map(ExerciseHint::getId).collect(Collectors.toSet());
        assertThat(newlyImported.getExerciseHints().size()).isEqualTo(programmingExercise.getExerciseHints().size());
        assertThat(programmingExercise.getExerciseHints()).noneMatch(hint -> newHintIDs.contains(hint.getId()));
    }

    @Test
    public void importProgrammingExerciseBasis_hintsGotReplacedInStatement() throws MalformedURLException {
        final var imported = importExerciseBase();

        final var oldHintIDs = programmingExercise.getExerciseHints().stream().map(ExerciseHint::getId).collect(Collectors.toSet());
        final var newHintIDs = imported.getExerciseHints().stream().map(ExerciseHint::getId).collect(Collectors.toSet());
        final var matchString = ".*\\{[^{}]*%d[^{}]*\\}.*";
        final var importedStatement = imported.getProblemStatement();
        assertThat(oldHintIDs).noneMatch(hint -> importedStatement.matches(String.format(matchString, hint)));
        assertThat(newHintIDs).allMatch(hint -> importedStatement.matches(String.format(matchString, hint)));
    }

    @Test
    public void importProgrammingExerciseBasis_testsAndHintsHoldTheSameInformation() throws MalformedURLException {
        final var imported = importExerciseBase();

        // All copied hints/tests have the same content are are referenced to the new exercise
        assertThat(imported.getExerciseHints()).allMatch(hint -> programmingExercise.getExerciseHints().stream().anyMatch(
                oldHint -> oldHint.getContent().equals(hint.getContent()) && oldHint.getTitle().equals(hint.getTitle()) && hint.getExercise().getId().equals(imported.getId())));
        assertThat(imported.getTestCases()).allMatch(test -> programmingExercise.getTestCases().stream().anyMatch(oldTest -> test.getExercise().getId().equals(imported.getId())
                && oldTest.getTestName().equals(test.getTestName()) && oldTest.getWeight().equals(test.getWeight())));
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void importExercise_tutor_forbidden() throws Exception {
        final var toBeImported = createToBeImported();
        request.post(BASE_RESOURCE + "import/" + programmingExercise.getId(), toBeImported, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void importExercise_user_forbidden() throws Exception {
        final var toBeImported = createToBeImported();
        request.post(BASE_RESOURCE + "import/" + programmingExercise.getId(), toBeImported, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importExercise_instructor_correctBuildPlansAndRepositories() throws Exception {
        final var toBeImported = createToBeImported();
        final var verifications = new LinkedList<Verifiable>();
        final var projectKey = toBeImported.getProjectKey();
        final var sourceProjectKey = programmingExercise.getProjectKey();
        final var templateRepoName = (projectKey + "-" + RepositoryType.TEMPLATE.getName()).toLowerCase();
        final var solutionRepoName = (projectKey + "-" + RepositoryType.SOLUTION.getName()).toLowerCase();
        final var testsRepoName = (projectKey + "-" + RepositoryType.TESTS.getName()).toLowerCase();
        var nextParticipationId = programmingExercise.getTemplateParticipation().getId() + 1;
        final var artemisSolutionHookPath = ARTEMIS_SERVER_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + nextParticipationId++;
        final var artemisTemplateHookPath = ARTEMIS_SERVER_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + nextParticipationId++;
        final var artemisTestsHookPath = ARTEMIS_SERVER_URL + TEST_CASE_CHANGED_API_PATH + (programmingExercise.getId() + 1);

        verifications.add(bambooRequestMockProvider.mockCopyBuildPlan(programmingExercise.getProjectKey(), TEMPLATE.getName(), projectKey, TEMPLATE.getName()));
        verifications.add(bambooRequestMockProvider.mockCopyBuildPlan(programmingExercise.getProjectKey(), SOLUTION.getName(), projectKey, SOLUTION.getName()));
        verifications.add(bambooRequestMockProvider.mockEnablePlan(projectKey, TEMPLATE.getName()));
        verifications.add(bambooRequestMockProvider.mockEnablePlan(projectKey, SOLUTION.getName()));
        bitbucketRequestMockProvider.mockCreateProjectForExercise(toBeImported);
        bitbucketRequestMockProvider.mockCopyRepository(sourceProjectKey, projectKey, programmingExercise.getTemplateRepositoryName(), templateRepoName);
        bitbucketRequestMockProvider.mockCopyRepository(sourceProjectKey, projectKey, programmingExercise.getSolutionRepositoryName(), solutionRepoName);
        bitbucketRequestMockProvider.mockCopyRepository(sourceProjectKey, projectKey, programmingExercise.getTestRepositoryName(), testsRepoName);
        bitbucketRequestMockProvider.mockGetExistingWebhooks(projectKey, templateRepoName);
        bitbucketRequestMockProvider.mockAddWebhook(projectKey, templateRepoName, artemisTemplateHookPath);
        bitbucketRequestMockProvider.mockGetExistingWebhooks(projectKey, solutionRepoName);
        bitbucketRequestMockProvider.mockAddWebhook(projectKey, solutionRepoName, artemisSolutionHookPath);
        bitbucketRequestMockProvider.mockGetExistingWebhooks(projectKey, testsRepoName);
        bitbucketRequestMockProvider.mockAddWebhook(projectKey, testsRepoName, artemisTestsHookPath);
        bambooRequestMockProvider.mockGiveProjectPermissions(toBeImported);
        bambooRequestMockProvider.mockUpdatePlanRepository(toBeImported, TEMPLATE.getName(), ASSIGNMENT_REPO_NAME, templateRepoName, List.of(ASSIGNMENT_REPO_NAME));
        bambooRequestMockProvider.mockUpdatePlanRepository(toBeImported, TEMPLATE.getName(), TEST_REPO_NAME, testsRepoName, List.of());
        bambooRequestMockProvider.mockUpdatePlanRepository(toBeImported, SOLUTION.getName(), ASSIGNMENT_REPO_NAME, solutionRepoName, List.of());
        bambooRequestMockProvider.mockUpdatePlanRepository(toBeImported, SOLUTION.getName(), TEST_REPO_NAME, testsRepoName, List.of());
        bambooRequestMockProvider.mockTriggerBuild(toBeImported.getProjectKey() + "-" + TEMPLATE.getName());
        bambooRequestMockProvider.mockTriggerBuild(toBeImported.getProjectKey() + "-" + SOLUTION.getName());

        request.postWithResponseBody(BASE_RESOURCE + "import/" + programmingExercise.getId(), toBeImported, ProgrammingExercise.class, HttpStatus.OK);

        for (final var verifiable : verifications) {
            verifiable.performVerification();
        }
    }

    @Test
    @WithMockUser(username = "instructorother1", roles = "INSTRUCTOR")
    public void searchExercises_instructor_shouldOnlyGetResultsFromOwningCourses() throws Exception {
        final var search = new PageableSearchDTO<String>();
        search.setPage(0);
        search.setPageSize(10);
        search.setSearchTerm("");
        search.setSortedColumn(ProgrammingExercise.ProgrammingExerciseSearchColumn.ID.name());
        search.setSortingOrder(SortingOrder.ASCENDING);
        final var mapType = new TypeToken<Map<String, String>>() {
        }.getType();
        final var gson = new Gson();
        final Map<String, String> params = new Gson().fromJson(gson.toJson(search), mapType);
        final var paramMap = new LinkedMultiValueMap<String, String>();
        params.forEach(paramMap::add);

        final var result = request.get(BASE_RESOURCE, HttpStatus.OK, SearchResultPageDTO.class, paramMap);
        assertThat(result.getResultsOnPage()).isEmpty();
    }

    private ProgrammingExercise importExerciseBase() {
        final var toBeImported = createToBeImported();
        return programmingExerciseImportService.importProgrammingExerciseBasis(programmingExercise, toBeImported);
    }

    private ProgrammingExercise createToBeImported() {
        return ModelFactory.generateToBeImportedProgrammingExercise("Test", "TST", programmingExercise, additionalEmptyCourse);
    }
}
