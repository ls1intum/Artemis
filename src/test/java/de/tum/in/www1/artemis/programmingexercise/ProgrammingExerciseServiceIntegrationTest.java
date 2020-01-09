package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.MalformedURLException;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import de.tum.in.www1.artemis.AbstractSpringIntegrationTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.ProgrammingExerciseService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

public class ProgrammingExerciseServiceIntegrationTest extends AbstractSpringIntegrationTest {

    private static final String BASE_RESOURCE = "/api/programming-exercises/";

    private static final String DUMMY_URL = "https://te12ste@repobruegge.in.tum.de/scm/TEST2019TEST/testexercise-te12ste.git";

    @Autowired
    ProgrammingExerciseService programmingExerciseService;

    @Autowired
    DatabaseUtilService databse;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    RequestUtilService request;

    private Course additionalEmptyCourse;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    public void setUp() throws MalformedURLException {
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

        doReturn(toBeImported.getTemplateBuildPlanId()).when(continuousIntegrationService).copyBuildPlan(anyString(), eq(BuildPlanType.TEMPLATE.getName()), anyString(),
                anyString(), eq(BuildPlanType.TEMPLATE.getName()));
        doReturn(toBeImported.getSolutionBuildPlanId()).when(continuousIntegrationService).copyBuildPlan(anyString(), eq(BuildPlanType.SOLUTION.getName()), anyString(),
                anyString(), eq(BuildPlanType.SOLUTION.getName()));
        doNothing().when(continuousIntegrationService).enablePlan(anyString(), anyString());
        doReturn(new DummyRepositoryUrl(DUMMY_URL)).when(versionControlService).getCloneRepositoryUrl(anyString(), anyString());
        doNothing().when(versionControlService).createProjectForExercise(any());
        doReturn(new DummyRepositoryUrl(DUMMY_URL)).when(versionControlService).copyRepository(anyString(), anyString(), anyString(), anyString());
        doNothing().when(versionControlService).addWebHooksForExercise(any());
        doNothing().when(continuousIntegrationService).giveProjectPermissions(anyString(), any(), any());
        doNothing().when(continuousIntegrationService).updatePlanRepository(any(), any(), any(), any(), any(), any());
        doNothing().when(continuousIntegrationService).triggerBuild(any());

        request.postWithResponseBody(BASE_RESOURCE + "import/" + programmingExercise.getId(), toBeImported, ProgrammingExercise.class, HttpStatus.OK);
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

    private ProgrammingExercise importExerciseBase() throws MalformedURLException {
        final var toBeImported = createToBeImported();
        final var templateRepoName = toBeImported.getProjectKey().toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        final var solutionRepoName = toBeImported.getProjectKey().toLowerCase() + "-" + RepositoryType.SOLUTION.getName();
        final var testRepoName = toBeImported.getProjectKey().toLowerCase() + "-" + RepositoryType.TESTS.getName();
        when(versionControlService.getCloneRepositoryUrl(toBeImported.getProjectKey(), templateRepoName)).thenReturn(new DummyRepositoryUrl(DUMMY_URL));
        when(versionControlService.getCloneRepositoryUrl(toBeImported.getProjectKey(), solutionRepoName)).thenReturn(new DummyRepositoryUrl(DUMMY_URL));
        when(versionControlService.getCloneRepositoryUrl(toBeImported.getProjectKey(), testRepoName)).thenReturn(new DummyRepositoryUrl(DUMMY_URL));

        return programmingExerciseService.importProgrammingExerciseBasis(programmingExercise, toBeImported);
    }

    private ProgrammingExercise createToBeImported() {
        return ModelFactory.generateToBeImportedProgrammingExercise("Test", "TST", programmingExercise, additionalEmptyCourse);
    }

    private static final class DummyRepositoryUrl extends VcsRepositoryUrl {

        public DummyRepositoryUrl(String url) throws MalformedURLException {
            super(url);
        }

        @Override
        public VcsRepositoryUrl withUser(String username) {
            return null;
        }
    }
}
