package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseImportService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

public class ProgrammingExerciseServiceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String BASE_RESOURCE = "/api/programming-exercises/";

    @Autowired
    ProgrammingExerciseService programmingExerciseService;

    @Autowired
    ProgrammingExerciseImportService programmingExerciseImportService;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    private Course additionalEmptyCourse;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    public void setUp() {
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();
        database.addUsers(1, 1, 0, 1);
        database.addInstructor("other-instructors", "instructorother");
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        additionalEmptyCourse = database.addEmptyCourse();
        programmingExercise = programmingExerciseRepository.findAll().get(0);
        database.addHintsToExercise(programmingExercise);
        database.addHintsToProblemStatement(programmingExercise);
        database.addStaticCodeAnalysisCategoriesToProgrammingExercise(programmingExercise);

        // Load again to fetch changes to statement and hints while keeping eager refs
        programmingExercise = database.loadProgrammingExerciseWithEagerReferences(programmingExercise);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExerciseBasis_baseReferencesGotCloned() {
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
        assertThat(newlyImported.getTotalNumberOfAssessments()).isNull();
        assertThat(newlyImported.getNumberOfComplaints()).isNull();
        assertThat(newlyImported.getNumberOfMoreFeedbackRequests()).isNull();
        assertThat(newlyImported.getNumberOfSubmissions()).isNull();
        assertThat(newlyImported.getAttachments()).isNull();
        assertThat(newlyImported.getTutorParticipations()).isNull();
        assertThat(newlyImported.getExampleSubmissions()).isNull();
        assertThat(newlyImported.getPosts()).isNull();
        assertThat(newlyImported.getStudentParticipations()).isNull();
        final var newTestCaseIDs = newlyImported.getTestCases().stream().map(ProgrammingExerciseTestCase::getId).collect(Collectors.toSet());
        assertThat(newlyImported.getTestCases().size()).isEqualTo(programmingExercise.getTestCases().size());
        assertThat(programmingExercise.getTestCases()).noneMatch(testCase -> newTestCaseIDs.contains(testCase.getId()));
        assertThat(programmingExercise.getTestCases()).usingElementComparatorIgnoringFields("id", "exercise", "tasks", "solutionEntries")
                .containsExactlyInAnyOrderElementsOf(newlyImported.getTestCases());
        final var newHintIDs = newlyImported.getExerciseHints().stream().map(ExerciseHint::getId).collect(Collectors.toSet());
        assertThat(newlyImported.getExerciseHints().size()).isEqualTo(programmingExercise.getExerciseHints().size());
        assertThat(programmingExercise.getExerciseHints()).noneMatch(hint -> newHintIDs.contains(hint.getId()));
        final var newStaticCodeAnalysisCategoriesIDs = newlyImported.getStaticCodeAnalysisCategories().stream().map(StaticCodeAnalysisCategory::getId).collect(Collectors.toSet());
        assertThat(newlyImported.getStaticCodeAnalysisCategories().size()).isEqualTo(programmingExercise.getStaticCodeAnalysisCategories().size());
        assertThat(programmingExercise.getStaticCodeAnalysisCategories()).noneMatch(category -> newStaticCodeAnalysisCategoriesIDs.contains(category.getId()));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExerciseBasis_hintsGotReplacedInStatement() {
        final var imported = importExerciseBase();

        final var oldHintIDs = programmingExercise.getExerciseHints().stream().map(ExerciseHint::getId).collect(Collectors.toSet());
        final var newHintIDs = imported.getExerciseHints().stream().map(ExerciseHint::getId).collect(Collectors.toSet());
        final var matchString = ".*\\{[^{}]*%d[^{}]*\\}.*";
        final var importedStatement = imported.getProblemStatement();
        assertThat(oldHintIDs).noneMatch(hint -> importedStatement.matches(String.format(matchString, hint)));
        assertThat(newHintIDs).allMatch(hint -> importedStatement.matches(String.format(matchString, hint)));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExerciseBasis_testsAndHintsHoldTheSameInformation() {
        final var imported = importExerciseBase();

        // All copied hints/tests have the same content are referenced to the new exercise
        assertThat(imported.getExerciseHints()).allMatch(hint -> programmingExercise.getExerciseHints().stream().anyMatch(
                oldHint -> oldHint.getContent().equals(hint.getContent()) && oldHint.getTitle().equals(hint.getTitle()) && hint.getExercise().getId().equals(imported.getId())));
        assertThat(imported.getTestCases()).allMatch(test -> programmingExercise.getTestCases().stream().anyMatch(oldTest -> test.getExercise().getId().equals(imported.getId())
                && oldTest.getTestName().equalsIgnoreCase(test.getTestName()) && oldTest.getWeight().equals(test.getWeight())));
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void importExercise_tutor_forbidden() throws Exception {
        final var toBeImported = createToBeImported();
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", programmingExercise.getId().toString()), toBeImported, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void importExercise_user_forbidden() throws Exception {
        final var toBeImported = createToBeImported();
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", programmingExercise.getId().toString()), toBeImported, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructorother1", roles = "INSTRUCTOR")
    public void testInstructorGetsResultsOnlyFromOwningCourses() throws Exception {
        final var search = database.configureSearch("");
        final var result = request.get(BASE_RESOURCE, HttpStatus.OK, SearchResultPageDTO.class, database.exerciseSearchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testInstructorGetsResultsFromOwningCoursesNotEmpty() throws Exception {
        final var search = database.configureSearch("Programming");
        final var result = request.get(BASE_RESOURCE, HttpStatus.OK, SearchResultPageDTO.class, database.exerciseSearchMapping(search));
        assertThat(result.getResultsOnPage().size()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSearchProgrammingExercisesWithProperSearchTerm() throws Exception {
        database.addCourseWithNamedProgrammingExerciseAndTestCases("Java JDK13");
        database.addCourseWithNamedProgrammingExerciseAndTestCases("Python");
        database.addCourseWithNamedProgrammingExerciseAndTestCases("Java JDK12");
        final var searchPython = database.configureSearch("Python");
        final var resultPython = request.get(BASE_RESOURCE, HttpStatus.OK, SearchResultPageDTO.class, database.exerciseSearchMapping(searchPython));
        assertThat(resultPython.getResultsOnPage().size()).isEqualTo(1);

        final var searchJava = database.configureSearch("Java");
        final var resultJava = request.get(BASE_RESOURCE, HttpStatus.OK, SearchResultPageDTO.class, database.exerciseSearchMapping(searchJava));
        assertThat(resultJava.getResultsOnPage().size()).isEqualTo(2);

        final var searchSwift = database.configureSearch("Swift");
        final var resultSwift = request.get(BASE_RESOURCE, HttpStatus.OK, SearchResultPageDTO.class, database.exerciseSearchMapping(searchSwift));
        assertThat(resultSwift.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testAdminGetsResultsFromAllCourses() throws Exception {
        database.addCourseInOtherInstructionGroupAndExercise("Programming");
        final var search = database.configureSearch("Programming");
        final var result = request.get(BASE_RESOURCE, HttpStatus.OK, SearchResultPageDTO.class, database.exerciseSearchMapping(search));
        assertThat(result.getResultsOnPage().size()).isEqualTo(2);
    }

    private ProgrammingExercise importExerciseBase() {
        final var toBeImported = createToBeImported();
        return programmingExerciseImportService.importProgrammingExerciseBasis(programmingExercise, toBeImported);
    }

    private ProgrammingExercise createToBeImported() {
        return ModelFactory.generateToBeImportedProgrammingExercise("Test", "TST", programmingExercise, additionalEmptyCourse);
    }

}
