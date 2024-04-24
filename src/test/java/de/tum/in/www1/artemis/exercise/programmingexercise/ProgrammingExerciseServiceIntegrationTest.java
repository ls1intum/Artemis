package de.tum.in.www1.artemis.exercise.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints.IMPORT;
import static de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints.ROOT;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.StaticCodeAnalysisCategory;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseImportBasicService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.ExerciseIntegrationTestService;
import de.tum.in.www1.artemis.util.PageableSearchUtilService;

class ProgrammingExerciseServiceIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "progexserviceintegration";

    private static final String BASE_RESOURCE = "/api/programming-exercises";

    @Autowired
    ProgrammingExerciseService programmingExerciseService;

    @Autowired
    ProgrammingExerciseImportBasicService programmingExerciseImportBasicService;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ExerciseIntegrationTestService exerciseIntegrationTestService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    private Course additionalEmptyCourse;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        userUtilService.addInstructor("other-instructors", TEST_PREFIX + "instructorother");
        additionalEmptyCourse = courseUtilService.addEmptyCourse();
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        // Needed, as we need the test cases for the next steps
        programmingExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(programmingExercise);
        programmingExerciseUtilService.addHintsToExercise(programmingExercise);
        programmingExerciseUtilService.addTasksToProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addSolutionEntriesToProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addCodeHintsToProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addStaticCodeAnalysisCategoriesToProgrammingExercise(programmingExercise);

        // Load again to fetch changes to statement and hints while keeping eager refs
        programmingExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(programmingExercise);
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

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("submissionPolicyProvider")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExerciseBasisWithSubmissionPolicy(SubmissionPolicy submissionPolicy) {
        final var imported = importExerciseBaseWithSubmissionPolicy(submissionPolicy);
        assertThat(imported.getSubmissionPolicy()).isNotNull();
        assertThat(imported.getSubmissionPolicy()).isInstanceOf(SubmissionPolicy.class);
        assertThat(imported.getSubmissionPolicy().getSubmissionLimit()).isEqualTo(5);
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
        final var search = pageableSearchUtilService.configureSearch("");
        final var result = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExercise.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetsResultsFromOwningCoursesNotEmpty() throws Exception {
        final var search = pageableSearchUtilService.configureSearch("Programming");
        final var result = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExercise.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorSearchTermMatchesId() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        testSearchTermMatchesId();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminSearchTermMatchesId() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        testSearchTermMatchesId();
    }

    private void testSearchTermMatchesId() throws Exception {
        final Course course = courseUtilService.addEmptyCourse();
        final var now = ZonedDateTime.now();
        ProgrammingExercise exercise = ProgrammingExerciseFactory.generateProgrammingExercise(now.minusDays(1), now.minusHours(2), course);
        exercise.setTitle("LoremIpsum");
        exercise = programmingExerciseRepository.save(exercise);
        var exerciseId = exercise.getId();

        final var searchTerm = pageableSearchUtilService.configureSearch(exerciseId.toString());
        final var searchResult = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExercise.class, pageableSearchUtilService.searchMapping(searchTerm));
        assertThat(searchResult.getResultsOnPage().stream().filter(programmingExercise -> (Objects.equals(programmingExercise.getId(), exerciseId)))).hasSize(1);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { false, true })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCourseAndExamFiltersAsInstructor(boolean withSCA) throws Exception {
        testCourseAndExamFilters(withSCA, "testCourseAndExamFiltersAsInstructor" + withSCA);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { false, true })
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCourseAndExamFiltersAsAdmin(boolean withSCA) throws Exception {
        testCourseAndExamFilters(withSCA, "testCourseAndExamFiltersAsAdmin" + withSCA);
    }

    private void testCourseAndExamFilters(boolean withSCA, String programmingExerciseTitle) throws Exception {
        programmingExerciseUtilService.addCourseWithNamedProgrammingExerciseAndTestCases(programmingExerciseTitle, withSCA);
        programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise(programmingExerciseTitle + "-Morpork", programmingExerciseTitle + "Morpork");
        exerciseIntegrationTestService.testCourseAndExamFilters("/api/programming-exercises", programmingExerciseTitle);
        testSCAFilter(programmingExerciseTitle, withSCA);
    }

    private void testSCAFilter(String searchTerm, boolean expectSca) throws Exception {
        var search = pageableSearchUtilService.configureSearch(searchTerm);
        var filters = pageableSearchUtilService.searchMapping(search);

        // We should get both exercises when we don't filter for SCA only (other endpoint)
        var result = request.getSearchResult("/api/programming-exercises", HttpStatus.OK, ProgrammingExercise.class, filters);
        assertThat(result.getResultsOnPage()).hasSize(2);

        filters = pageableSearchUtilService.searchMapping(search);
        filters.add("programmingLanguage", "JAVA");

        // The exam exercise is always created with SCA deactivated
        // expectSca true -> 1 result, false -> 0 results
        result = request.getSearchResult("/api/programming-exercises/with-sca", HttpStatus.OK, ProgrammingExercise.class, filters);
        assertThat(result.getResultsOnPage()).hasSize(expectSca ? 1 : 0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSearchProgrammingExercisesWithProperSearchTerm() throws Exception {
        programmingExerciseUtilService.addCourseWithNamedProgrammingExerciseAndTestCases("Java JDK13");
        programmingExerciseUtilService.addCourseWithNamedProgrammingExerciseAndTestCases("Python");
        programmingExerciseUtilService.addCourseWithNamedProgrammingExerciseAndTestCases("Java JDK12");
        final var searchPython = pageableSearchUtilService.configureSearch("Python");
        final var resultPython = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExercise.class, pageableSearchUtilService.searchMapping(searchPython));
        assertThat(resultPython.getResultsOnPage()).hasSize(1);

        final var searchJava = pageableSearchUtilService.configureSearch("Java");
        final var resultJava = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExercise.class, pageableSearchUtilService.searchMapping(searchJava));
        assertThat(resultJava.getResultsOnPage()).hasSize(2);

        final var searchSwift = pageableSearchUtilService.configureSearch("Swift");
        final var resultSwift = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExercise.class, pageableSearchUtilService.searchMapping(searchSwift));
        assertThat(resultSwift.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminGetsResultsFromAllCourses() throws Exception {
        // Use unique name for exercise to not query exercises from other tests
        var title = "testAdminGetsResultsFromAllCourses-Programming";
        programmingExercise.setTitle(title);
        programmingExerciseRepository.save(programmingExercise);

        var otherCourse = courseUtilService.addCourseInOtherInstructionGroupAndExercise("Programming");
        var otherProgrammingExercise = exerciseUtilService.getFirstExerciseWithType(otherCourse, ProgrammingExercise.class);
        otherProgrammingExercise.setTitle(title);
        programmingExerciseRepository.save(otherProgrammingExercise);

        final var search = pageableSearchUtilService.configureSearch(title);
        final var result = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExercise.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testNoBuildPlanAccessSecretForImportedExercise() {
        var importedExercise = programmingExerciseImportBasicService.importProgrammingExerciseBasis(programmingExercise, createToBeImported());
        assertThat(programmingExercise.getBuildPlanAccessSecret()).isEqualTo(importedExercise.getBuildPlanAccessSecret()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDifferentBuildPlanAccessSecretForImportedExercise() {
        programmingExerciseUtilService.addBuildPlanAndSecretToProgrammingExercise(programmingExercise, "text");
        var importedExercise = programmingExerciseImportBasicService.importProgrammingExerciseBasis(programmingExercise, createToBeImported());
        assertThat(programmingExercise.getBuildPlanAccessSecret()).isNotNull().isNotEqualTo(importedExercise.getBuildPlanAccessSecret());
    }

    private ProgrammingExercise importExerciseBase() {
        final var toBeImported = createToBeImported();
        return programmingExerciseImportBasicService.importProgrammingExerciseBasis(programmingExercise, toBeImported);
    }

    private ProgrammingExercise importExerciseBaseWithSubmissionPolicy(SubmissionPolicy submissionPolicy) {
        final var toBeImported = createToBeImportedWithSubmissionPolicy(submissionPolicy);
        return programmingExerciseImportBasicService.importProgrammingExerciseBasis(programmingExercise, toBeImported);
    }

    private ProgrammingExercise createToBeImported() {
        return ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("Test", "TST", programmingExercise, additionalEmptyCourse);
    }

    private ProgrammingExercise createToBeImportedWithSubmissionPolicy(SubmissionPolicy submissionPolicy) {
        var exercise = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("Test", "TST", programmingExercise, additionalEmptyCourse);
        if (submissionPolicy != null) {
            submissionPolicy.setProgrammingExercise(exercise);
            exercise.setSubmissionPolicy(submissionPolicy);
        }
        return exercise;
    }

    private static Stream<SubmissionPolicy> submissionPolicyProvider() {
        var lockRepoPolicy = new LockRepositoryPolicy();
        lockRepoPolicy.setSubmissionLimit(5);
        lockRepoPolicy.setActive(true);

        var submissionPenaltyPolicy = new SubmissionPenaltyPolicy();
        submissionPenaltyPolicy.setSubmissionLimit(5);
        submissionPenaltyPolicy.setExceedingPenalty(3.0);
        submissionPenaltyPolicy.setActive(true);

        return Stream.of(lockRepoPolicy, submissionPenaltyPolicy);
    }

}
