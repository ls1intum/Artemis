package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.IMPORT;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.ROOT;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

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
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseImportBasicService;
import de.tum.in.www1.artemis.util.ExerciseIntegrationTestUtils;
import de.tum.in.www1.artemis.util.ModelFactory;

class ProgrammingExerciseServiceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "progexserviceintegration";

    private static final String BASE_RESOURCE = "/api/programming-exercises/";

    @Autowired
    private ProgrammingExerciseImportBasicService programmingExerciseImportBasicService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ExerciseIntegrationTestUtils exerciseIntegrationTestUtils;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    private CodeHintRepository codeHintRepository;

    private Course additionalEmptyCourse;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setUp() {
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();
        database.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        database.addInstructor("other-instructors", TEST_PREFIX + "instructorother");
        additionalEmptyCourse = database.addEmptyCourse();
        var course = database.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExerciseBasis_baseReferencesGotCloned() {
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
        assertThat(newlyImported.getTutorParticipations()).isNull();
        assertThat(newlyImported.getExampleSubmissions()).isNull();
        assertThat(newlyImported.getPosts()).isNull();
        assertThat(newlyImported.getStudentParticipations()).isNull();
        final var newTestCaseIDs = newlyImported.getTestCases().stream().map(ProgrammingExerciseTestCase::getId).collect(Collectors.toSet());
        assertThat(newlyImported.getTestCases()).hasSameSizeAs(programmingExercise.getTestCases());
        assertThat(programmingExercise.getTestCases()).noneMatch(testCase -> newTestCaseIDs.contains(testCase.getId()));
        assertThat(programmingExercise.getTestCases()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "exercise", "tasks", "solutionEntries", "coverageEntries")
                .containsExactlyInAnyOrderElementsOf(newlyImported.getTestCases());
        final var newHintIDs = newlyImported.getExerciseHints().stream().map(ExerciseHint::getId).collect(Collectors.toSet());
        assertThat(newlyImported.getExerciseHints()).hasSameSizeAs(programmingExercise.getExerciseHints());
        assertThat(programmingExercise.getExerciseHints()).noneMatch(hint -> newHintIDs.contains(hint.getId()));
        final var newStaticCodeAnalysisCategoriesIDs = newlyImported.getStaticCodeAnalysisCategories().stream().map(StaticCodeAnalysisCategory::getId).collect(Collectors.toSet());
        assertThat(newlyImported.getStaticCodeAnalysisCategories()).hasSameSizeAs(programmingExercise.getStaticCodeAnalysisCategories());
        assertThat(programmingExercise.getStaticCodeAnalysisCategories()).noneMatch(category -> newStaticCodeAnalysisCategoriesIDs.contains(category.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExerciseBasis_testsAndHintsHoldTheSameInformation() {
        var importedExercise = importExerciseBase();
        importedExercise = database.loadProgrammingExerciseWithEagerReferences(importedExercise);

        // All copied hints/tests have the same content are referenced to the new exercise
        ProgrammingExercise finalImportedExercise = importedExercise;
        assertThat(importedExercise.getExerciseHints())
                .allMatch(hint -> programmingExercise.getExerciseHints().stream().anyMatch(oldHint -> oldHint.getContent().equals(hint.getContent())
                        && oldHint.getTitle().equals(hint.getTitle()) && hint.getExercise().getId().equals(finalImportedExercise.getId())));

        var importedCodeHints = codeHintRepository.findByExerciseIdWithSolutionEntries(importedExercise.getId());
        var originalCodeHints = codeHintRepository.findByExerciseIdWithSolutionEntries(programmingExercise.getId());

        for (var importedCodeHint : importedCodeHints) {
            // TODO: simplify the following statement
            assertThat(importedCodeHint).matches(codeHint -> originalCodeHints.stream()
                    .anyMatch(originalHint -> originalHint.getTitle().equals(codeHint.getTitle())
                            && originalHint.getProgrammingExerciseTask().getTaskName().equals(codeHint.getProgrammingExerciseTask().getTaskName())
                            && codeHint.getSolutionEntries().size() == 1 && originalHint.getSolutionEntries().stream().findFirst().orElseThrow().getCode()
                                    .equals(codeHint.getSolutionEntries().stream().findFirst().orElseThrow().getCode())));
        }

        var importedTestCases = programmingExerciseTestCaseRepository.findByExerciseIdWithSolutionEntries(importedExercise.getId());
        var originalTestCases = programmingExerciseTestCaseRepository.findByExerciseIdWithSolutionEntries(programmingExercise.getId());

        for (var importedTestCase : importedTestCases) {
            // TODO: simplify the following statement
            assertThat(importedTestCase).matches(test -> originalTestCases.stream()
                    .anyMatch(originalTest -> test.getExercise().getId().equals(finalImportedExercise.getId()) && originalTest.getTestName().equalsIgnoreCase(test.getTestName())
                            && originalTest.getWeight().equals(test.getWeight()) && test.getSolutionEntries().size() == 1 && originalTest.getSolutionEntries().stream().findFirst()
                                    .orElseThrow().getCode().equals(test.getSolutionEntries().stream().findFirst().orElseThrow().getCode())));
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void importExercise_tutor_forbidden() throws Exception {
        final var toBeImported = createToBeImported();
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", programmingExercise.getId().toString()), toBeImported, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void importExercise_user_forbidden() throws Exception {
        final var toBeImported = createToBeImported();
        request.post(ROOT + IMPORT.replace("{sourceExerciseId}", programmingExercise.getId().toString()), toBeImported, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructorother1", roles = "INSTRUCTOR")
    void testInstructorGetsResultsOnlyFromOwningCourses() throws Exception {
        final var search = database.configureSearch("");
        final var result = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExercise.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetsResultsFromOwningCoursesNotEmpty() throws Exception {
        final var search = database.configureSearch("Programming");
        final var result = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExercise.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNotEmpty();
        // TODO: better assertions
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorSearchTermMatchesId() throws Exception {
        database.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        testSearchTermMatchesId();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminSearchTermMatchesId() throws Exception {
        database.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        testSearchTermMatchesId();
    }

    private void testSearchTermMatchesId() throws Exception {
        final Course course = database.addEmptyCourse();
        final var now = ZonedDateTime.now();
        ProgrammingExercise exercise = ModelFactory.generateProgrammingExercise(now.minusDays(1), now.minusHours(2), course);
        exercise.setTitle("LoremIpsum");
        exercise = programmingExerciseRepository.save(exercise);
        var exerciseId = exercise.getId();

        final var searchTerm = database.configureSearch(exerciseId.toString());
        final var searchResult = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExercise.class, database.searchMapping(searchTerm));
        assertThat(searchResult.getResultsOnPage().stream().filter(programmingExercise -> Objects.equals(programmingExercise.getId(), exerciseId))).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCourseAndExamFiltersAsInstructor() throws Exception {
        String randomString = UUID.randomUUID().toString();
        database.addCourseWithNamedProgrammingExerciseAndTestCases(randomString);
        database.addCourseExamExerciseGroupWithOneProgrammingExercise(randomString + "-Morpork", randomString + "Morpork");
        exerciseIntegrationTestUtils.testCourseAndExamFilters("/api/programming-exercises", randomString, ProgrammingExercise.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCourseAndExamFiltersAsAdmin() throws Exception {
        String randomString = UUID.randomUUID().toString();
        database.addCourseWithNamedProgrammingExerciseAndTestCases(randomString);
        database.addCourseExamExerciseGroupWithOneProgrammingExercise(randomString + "-Morpork", randomString + "Morpork");
        exerciseIntegrationTestUtils.testCourseAndExamFilters("/api/programming-exercises", randomString, ProgrammingExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSearchProgrammingExercisesWithProperSearchTerm() throws Exception {
        database.addCourseWithNamedProgrammingExerciseAndTestCases("Java JDK13");
        database.addCourseWithNamedProgrammingExerciseAndTestCases("Python");
        database.addCourseWithNamedProgrammingExerciseAndTestCases("Java JDK12");
        final var searchPython = database.configureSearch("Python");
        final var resultPython = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExercise.class, database.searchMapping(searchPython));
        assertThat(resultPython.getResultsOnPage()).hasSize(1);

        final var searchJava = database.configureSearch("Java");
        final var resultJava = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExercise.class, database.searchMapping(searchJava));
        assertThat(resultJava.getResultsOnPage()).hasSize(2);

        final var searchSwift = database.configureSearch("Swift");
        final var resultSwift = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExercise.class, database.searchMapping(searchSwift));
        assertThat(resultSwift.getResultsOnPage()).isNullOrEmpty();

        // TODO: better assertions
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminGetsResultsFromAllCourses() throws Exception {
        // Use unique name for exercise to not query exercises from other tests
        var title = "testAdminGetsResultsFromAllCourses-Programming";
        programmingExercise.setTitle(title);
        programmingExerciseRepository.save(programmingExercise);

        var otherCourse = database.addCourseInOtherInstructionGroupAndExercise("Programming");
        var otherProgrammingExercise = database.getFirstExerciseWithType(otherCourse, ProgrammingExercise.class);
        otherProgrammingExercise.setTitle(title);
        programmingExerciseRepository.save(otherProgrammingExercise);

        final var search = database.configureSearch(title);
        final var result = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExercise.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(2);
        // TODO: better assertions
    }

    private ProgrammingExercise importExerciseBase() {
        final var toBeImported = createToBeImported();
        return programmingExerciseImportBasicService.importProgrammingExerciseBasis(programmingExercise, toBeImported);
    }

    private ProgrammingExercise createToBeImported() {
        return ModelFactory.generateToBeImportedProgrammingExercise("Test", "TST", programmingExercise, additionalEmptyCourse);
    }

}
