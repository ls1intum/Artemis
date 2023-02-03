package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.IMPORT;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.ROOT;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import de.tum.in.www1.artemis.util.ExerciseIntegrationTestUtils;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

class ProgrammingExerciseServiceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "progexserviceintegration";

    private static final String BASE_RESOURCE = "/api/programming-exercises/";

    @Autowired
    ProgrammingExerciseService programmingExerciseService;

    @Autowired
    ProgrammingExerciseImportBasicService programmingExerciseImportBasicService;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ExerciseIntegrationTestUtils exerciseIntegrationTestUtils;

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
        assertThat(newlyImported.getAttachments()).isNull();
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
        final var result = request.get(BASE_RESOURCE, HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetsResultsFromOwningCoursesNotEmpty() throws Exception {
        final var search = database.configureSearch("Programming");
        final var result = request.get(BASE_RESOURCE, HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNotEmpty();
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
        final var searchResult = request.get(BASE_RESOURCE, HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(searchTerm));
        assertThat(searchResult.getResultsOnPage().stream().filter(result -> ((int) ((LinkedHashMap<String, ?>) result).get("id")) == exerciseId.intValue())).hasSize(1);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { false, true })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCourseAndExamFiltersAsInstructor(boolean withSCA) throws Exception {
        testCourseAndExamFilters(withSCA);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { false, true })
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCourseAndExamFiltersAsAdmin(boolean withSCA) throws Exception {
        testCourseAndExamFilters(withSCA);
    }

    private void testCourseAndExamFilters(boolean withSCA) throws Exception {
        String randomString = UUID.randomUUID().toString();
        database.addCourseWithNamedProgrammingExerciseAndTestCases(randomString, withSCA);
        database.addCourseExamExerciseGroupWithOneProgrammingExercise(randomString + "-Morpork", randomString + "Morpork");
        exerciseIntegrationTestUtils.testCourseAndExamFilters("/api/programming-exercises/", randomString);
        testSCAFilter(randomString, withSCA);
    }

    private void testSCAFilter(String searchTerm, boolean expectSca) throws Exception {
        var search = database.configureSearch(searchTerm);
        var filters = database.searchMapping(search);

        // We should get both exercises when we don't filter for SCA only (other endpoint)
        var result = request.get("/api/programming-exercises", HttpStatus.OK, SearchResultPageDTO.class, filters);
        assertThat(result.getResultsOnPage()).hasSize(2);

        filters = database.searchMapping(search);
        filters.add("programmingLanguage", "JAVA");

        // The exam exercise is always created with SCA deactivated
        // expectSca true -> 1 result, false -> 0 results
        result = request.get("/api/programming-exercises/with-sca", HttpStatus.OK, SearchResultPageDTO.class, filters);
        assertThat(result.getResultsOnPage()).hasSize(expectSca ? 1 : 0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSearchProgrammingExercisesWithProperSearchTerm() throws Exception {
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
