package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.IMPORT;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.ROOT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.StaticCodeAnalysisCategory;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseImportBasicService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

public class ProgrammingExerciseServiceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String BASE_RESOURCE = "/api/programming-exercises/";

    @Autowired
    ProgrammingExerciseService programmingExerciseService;

    @Autowired
    ProgrammingExerciseImportBasicService programmingExerciseImportBasicService;

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
        // Needed, as we need the test cases for the next steps
        programmingExercise = database.loadProgrammingExerciseWithEagerReferences(programmingExercise);
        database.addHintsToExercise(programmingExercise);
        database.addTasksToProgrammingExercise(programmingExercise);
        database.addSolutionEntriesToProgrammingExercise(programmingExercise);
        database.addCodeHintsToProgrammingExercise(programmingExercise);
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
        assertThat(newlyImported).isNotSameAs(programmingExercise);
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
        assertThat(newlyImported.getTestCases()).hasSameSizeAs(programmingExercise.getTestCases());
        assertThat(programmingExercise.getTestCases()).noneMatch(testCase -> newTestCaseIDs.contains(testCase.getId()));
        assertThat(programmingExercise.getTestCases()).usingElementComparatorIgnoringFields("id", "exercise", "tasks", "solutionEntries", "coverageEntries")
                .containsExactlyInAnyOrderElementsOf(newlyImported.getTestCases());
        final var newHintIDs = newlyImported.getExerciseHints().stream().map(ExerciseHint::getId).collect(Collectors.toSet());
        assertThat(newlyImported.getExerciseHints()).hasSameSizeAs(programmingExercise.getExerciseHints());
        assertThat(programmingExercise.getExerciseHints()).noneMatch(hint -> newHintIDs.contains(hint.getId()));
        final var newStaticCodeAnalysisCategoriesIDs = newlyImported.getStaticCodeAnalysisCategories().stream().map(StaticCodeAnalysisCategory::getId).collect(Collectors.toSet());
        assertThat(newlyImported.getStaticCodeAnalysisCategories()).hasSameSizeAs(programmingExercise.getStaticCodeAnalysisCategories());
        assertThat(programmingExercise.getStaticCodeAnalysisCategories()).noneMatch(category -> newStaticCodeAnalysisCategoriesIDs.contains(category.getId()));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExerciseBasis_testsAndHintsHoldTheSameInformation() {
        final var imported = importExerciseBase();

        // All copied hints/tests have the same content are referenced to the new exercise
        assertThat(imported.getExerciseHints()).allMatch(hint -> programmingExercise.getExerciseHints().stream().anyMatch(
                oldHint -> oldHint.getContent().equals(hint.getContent()) && oldHint.getTitle().equals(hint.getTitle()) && hint.getExercise().getId().equals(imported.getId())));
        assertThat(imported.getExerciseHints().stream().filter(eh -> eh instanceof CodeHint).map(eh -> (CodeHint) eh).collect(Collectors.toSet()))
                .allMatch(codeHint -> programmingExercise.getExerciseHints().stream().filter(eh -> eh instanceof CodeHint).map(eh -> (CodeHint) eh)
                        .anyMatch(oldHint -> oldHint.getTitle().equals(codeHint.getTitle())
                                && oldHint.getProgrammingExerciseTask().getTaskName().equals(codeHint.getProgrammingExerciseTask().getTaskName())
                                && codeHint.getSolutionEntries().size() == 1 && oldHint.getSolutionEntries().stream().findFirst().orElseThrow().getCode()
                                        .equals(codeHint.getSolutionEntries().stream().findFirst().orElseThrow().getCode())));

        assertThat(imported.getTestCases()).allMatch(test -> programmingExercise.getTestCases().stream().anyMatch(oldTest -> test.getExercise().getId().equals(imported.getId())
                && oldTest.getTestName().equalsIgnoreCase(test.getTestName()) && oldTest.getWeight().equals(test.getWeight()) && test.getSolutionEntries().size() == 1
                && oldTest.getSolutionEntries().stream().findFirst().orElseThrow().getCode().equals(test.getSolutionEntries().stream().findFirst().orElseThrow().getCode())));
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
        final var result = request.get(BASE_RESOURCE, HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testInstructorGetsResultsFromOwningCoursesNotEmpty() throws Exception {
        final var search = database.configureSearch("Programming");
        final var result = request.get(BASE_RESOURCE, HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSearchProgrammingExercisesWithProperSearchTerm() throws Exception {
        database.addCourseWithNamedProgrammingExerciseAndTestCases("Java JDK13");
        database.addCourseWithNamedProgrammingExerciseAndTestCases("Python");
        database.addCourseWithNamedProgrammingExerciseAndTestCases("Java JDK12");
        final var searchPython = database.configureSearch("Python");
        final var resultPython = request.get(BASE_RESOURCE, HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(searchPython));
        assertThat(resultPython.getResultsOnPage()).hasSize(1);

        final var searchJava = database.configureSearch("Java");
        final var resultJava = request.get(BASE_RESOURCE, HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(searchJava));
        assertThat(resultJava.getResultsOnPage()).hasSize(2);

        final var searchSwift = database.configureSearch("Swift");
        final var resultSwift = request.get(BASE_RESOURCE, HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(searchSwift));
        assertThat(resultSwift.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testAdminGetsResultsFromAllCourses() throws Exception {
        database.addCourseInOtherInstructionGroupAndExercise("Programming");
        final var search = database.configureSearch("Programming");
        final var result = request.get(BASE_RESOURCE, HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(2);
    }

    private ProgrammingExercise importExerciseBase() {
        final var toBeImported = createToBeImported();
        return programmingExerciseImportBasicService.importProgrammingExerciseBasis(programmingExercise, toBeImported);
    }

    private ProgrammingExercise createToBeImported() {
        return ModelFactory.generateToBeImportedProgrammingExercise("Test", "TST", programmingExercise, additionalEmptyCourse);
    }

}
