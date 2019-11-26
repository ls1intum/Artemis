package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.MockBeans;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.ProgrammingExerciseService;
import de.tum.in.www1.artemis.service.connectors.BambooService;
import de.tum.in.www1.artemis.service.connectors.BitbucketService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles({ "artemis", "bamboo", "bitbucket", "jira" })
@MockBeans({ @MockBean(BambooService.class), @MockBean(BitbucketService.class) })
public class ProgrammingExerciseServiceIntegrationTest {

    private static final String BASE_RESOURCE = "/api/programming-exercises/";

    @Autowired
    BambooService bambooService;

    @Autowired
    BitbucketService bitbucketService;

    @Autowired
    ProgrammingExerciseService programmingExerciseService;

    @Autowired
    DatabaseUtilService databse;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    RequestUtilService request;

    private Course baseCourse;

    private Course additionalEmptyCourse;

    private ProgrammingExercise programmingExercise;

    private Set<ExerciseHint> hints;

    @BeforeEach
    public void setUp() throws MalformedURLException {
        databse.addUsers(1, 1, 1);
        databse.addInstructor("other-instructors", "instructorOther");
        baseCourse = databse.addCourseWithOneProgrammingExerciseAndTestCases();
        additionalEmptyCourse = databse.addEmptyCourse();
        programmingExercise = databse.loadProgrammingExerciseWithEagerReferences();
        hints = databse.addHintsToExercise(programmingExercise);
        databse.addHintsToProblemStatement(programmingExercise);

        // Load again to fetch changes to statement and hints while keeping eager refs
        programmingExercise = databse.loadProgrammingExerciseWithEagerReferences();

        ReflectionTestUtils.setField(bitbucketService, "log", LoggerFactory.getLogger(BitbucketService.class));
        ReflectionTestUtils.setField(bitbucketService, "BITBUCKET_SERVER_URL", new URL("http://testurl.de"));
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
        when(bambooService.copyBuildPlan(anyString(), matches(BuildPlanType.TEMPLATE.getName()), anyString(), anyString(), matches(BuildPlanType.TEMPLATE.getName())))
                .thenReturn(toBeImported.getTemplateBuildPlanId());
        when(bambooService.copyBuildPlan(anyString(), matches(BuildPlanType.SOLUTION.getName()), anyString(), anyString(), matches(BuildPlanType.SOLUTION.getName())))
                .thenReturn(toBeImported.getSolutionBuildPlanId());
        doCallRealMethod().when(bitbucketService).getCloneRepositoryUrl(anyString(), anyString());

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
        when(bitbucketService.getCloneRepositoryUrl(toBeImported.getProjectKey(), templateRepoName)).thenReturn(new DummyRepositoryUrl("http://template-url"));
        when(bitbucketService.getCloneRepositoryUrl(toBeImported.getProjectKey(), solutionRepoName)).thenReturn(new DummyRepositoryUrl("http://solution-url"));
        when(bitbucketService.getCloneRepositoryUrl(toBeImported.getProjectKey(), testRepoName)).thenReturn(new DummyRepositoryUrl("http://tests-url"));

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
