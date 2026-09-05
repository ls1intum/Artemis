package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseListItemDTO;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;

class ProgrammingExerciseServiceIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "progexserviceintegration";

    private static final String BASE_RESOURCE = "/api/programming/programming-exercises";

    private Course additionalEmptyCourse;

    private ProgrammingExercise programmingExercise;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        userUtilService.addInstructor(TEST_PREFIX + "other" + "instructor42");
        additionalEmptyCourse = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        var course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExerciseAndTestCases(TEST_PREFIX);
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        // Needed, as we need the test cases for the next steps
        programmingExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(programmingExercise);
        programmingExerciseUtilService.addTasksToProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addStaticCodeAnalysisCategoriesToProgrammingExercise(programmingExercise);

        // Load again to fetch changes to statement and hints while keeping eager refs
        programmingExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(programmingExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExerciseBasis_baseReferencesGotCloned() {
        // Re-fetch the imported exercise with all references eagerly initialized instead of relying on lazy proxies:
        // the import runs without an open session, so a returned lazy collection could not be read here.
        final var newlyImported = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(importExerciseBase());

        assertThat(newlyImported.getId()).isNotEqualTo(programmingExercise.getId());
        assertThat(newlyImported).isNotSameAs(programmingExercise);
        assertThat(newlyImported.getTemplateParticipation().getId()).isNotEqualTo(programmingExercise.getTemplateParticipation().getId());
        assertThat(newlyImported.getSolutionParticipation().getId()).isNotEqualTo(programmingExercise.getSolutionParticipation().getId());
        assertThat(newlyImported.getProgrammingLanguage()).isEqualTo(programmingExercise.getProgrammingLanguage());
        assertThat(newlyImported.getProjectKey()).isNotEqualTo(programmingExercise.getProjectKey());
        assertThat(newlyImported.getSolutionBuildPlanId()).isNotEqualTo(programmingExercise.getSolutionBuildPlanId());
        assertThat(newlyImported.getTemplateBuildPlanId()).isNotEqualTo(programmingExercise.getTemplateBuildPlanId());
        assertThat(newlyImported.getBuildConfig().hasSequentialTestRuns()).isEqualTo(programmingExercise.getBuildConfig().hasSequentialTestRuns());
        assertThat(newlyImported.isAllowOnlineEditor()).isEqualTo(programmingExercise.isAllowOnlineEditor());
        assertThat(newlyImported.getTotalNumberOfAssessments()).isNull();
        assertThat(newlyImported.getNumberOfComplaints()).isNull();
        assertThat(newlyImported.getNumberOfMoreFeedbackRequests()).isNull();
        assertThat(newlyImported.getNumberOfSubmissions()).isNull();
        // Student-facing data is not copied, so these collections are empty on the imported exercise.
        assertThat(newlyImported.getAttachments()).isEmpty();
        assertThat(newlyImported.getTutorParticipations()).isEmpty();
        assertThat(newlyImported.getExampleSubmissions()).isEmpty();
        assertThat(newlyImported.getStudentParticipations()).isEmpty();
        final var newTestCaseIDs = newlyImported.getTestCases().stream().map(ProgrammingExerciseTestCase::getId).collect(Collectors.toSet());
        assertThat(newlyImported.getTestCases()).hasSameSizeAs(programmingExercise.getTestCases());
        assertThat(programmingExercise.getTestCases()).noneMatch(testCase -> newTestCaseIDs.contains(testCase.getId()));
        assertThat(programmingExercise.getTestCases()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "exercise", "tasks")
                .containsExactlyInAnyOrderElementsOf(newlyImported.getTestCases());
        final var newStaticCodeAnalysisCategoriesIDs = newlyImported.getStaticCodeAnalysisCategories().stream().map(StaticCodeAnalysisCategory::getId).collect(Collectors.toSet());
        assertThat(newlyImported.getStaticCodeAnalysisCategories()).hasSameSizeAs(programmingExercise.getStaticCodeAnalysisCategories());
        assertThat(programmingExercise.getStaticCodeAnalysisCategories()).noneMatch(category -> newStaticCodeAnalysisCategoriesIDs.contains(category.getId()));
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
        request.post("/api/programming/programming-exercises/import?sourceExerciseId=" + programmingExercise.getId(), toBeImported, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void importExercise_user_forbidden() throws Exception {
        final var toBeImported = createToBeImported();
        request.post("/api/programming/programming-exercises/import?sourceExerciseId=" + programmingExercise.getId(), toBeImported, HttpStatus.FORBIDDEN);
    }

    /**
     * Exercise archives exported by older Artemis versions carry fields the current model no longer has. The import
     * request record ignores them, so the request must fail on the source exercise, never on the payload.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExercise_payloadWithUnknownFields_isTolerated() throws Exception {
        ObjectNode body = objectMapper.valueToTree(createToBeImported());
        body.put("removedLegacyProperty", "legacy value");
        body.putArray("removedLegacyCollection").add("legacy element");
        body.putObject("removedLegacyObject").put("nested", 1);
        String rawBody = objectMapper.writeValueAsString(body);

        // A payload the record could not parse would fail before any handler code runs and carry no error key.
        request.performMvcRequest(post(BASE_RESOURCE + "/import").queryParam("sourceExerciseId", "-1").contentType(MediaType.APPLICATION_JSON).content(rawBody))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorKey").value("invalidSourceExerciseId"));

        // The same payload binds far enough to pass every settings validation and to resolve the target course; only
        // the source exercise is missing.
        request.performMvcRequest(
                post(BASE_RESOURCE + "/import").queryParam("sourceExerciseId", String.valueOf(Integer.MAX_VALUE)).contentType(MediaType.APPLICATION_JSON).content(rawBody))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "other" + "instructor42", roles = "INSTRUCTOR")
    void testInstructorGetsResultsOnlyFromOwningCourses() throws Exception {
        final var search = pageableSearchUtilService.configureSearch("");
        final var result = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExerciseListItemDTO.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetsResultsFromOwningCoursesNotEmpty() throws Exception {
        final var search = pageableSearchUtilService.configureSearch("Programming");
        final var result = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExerciseListItemDTO.class, pageableSearchUtilService.searchMapping(search));
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
        final Course course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        final var now = ZonedDateTime.now();
        ProgrammingExercise exercise = ProgrammingExerciseFactory.generateProgrammingExercise(now.minusDays(1), now.minusHours(2), course);
        exercise.setTitle("LoremIpsum");
        exercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(exercise.getBuildConfig()));
        exercise = programmingExerciseRepository.save(exercise);
        var exerciseId = exercise.getId();

        final var searchTerm = pageableSearchUtilService.configureSearch(exerciseId.toString());
        final var searchResult = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExerciseListItemDTO.class, pageableSearchUtilService.searchMapping(searchTerm));
        assertThat(searchResult.getResultsOnPage().stream().filter(listItem -> Objects.equals(listItem.id(), exerciseId))).hasSize(1);
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
        programmingExerciseUtilService.addEnrolledCourseWithNamedProgrammingExerciseAndTestCases(programmingExerciseTitle, withSCA, TEST_PREFIX);
        programmingExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneProgrammingExercise(programmingExerciseTitle + "-Morpork", programmingExerciseTitle + "Morpork",
                TEST_PREFIX);
        exerciseIntegrationTestService.testCourseAndExamFilters("/api/programming/programming-exercises", programmingExerciseTitle);
        testSCAFilter(programmingExerciseTitle, withSCA);
    }

    private void testSCAFilter(String searchTerm, boolean expectSca) throws Exception {
        var search = pageableSearchUtilService.configureSearch(searchTerm);
        var filters = pageableSearchUtilService.searchMapping(search);

        // We should get both exercises when we don't filter for SCA only (other endpoint)
        var result = request.getSearchResult("/api/programming/programming-exercises", HttpStatus.OK, ProgrammingExerciseListItemDTO.class, filters);
        assertThat(result.getResultsOnPage()).hasSize(2);

        filters = pageableSearchUtilService.searchMapping(search);
        filters.add("programmingLanguage", "JAVA");

        // The exam exercise is always created with SCA deactivated
        // expectSca true -> 1 result, false -> 0 results
        result = request.getSearchResult("/api/programming/programming-exercises/with-sca", HttpStatus.OK, ProgrammingExerciseListItemDTO.class, filters);
        assertThat(result.getResultsOnPage()).hasSize(expectSca ? 1 : 0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSearchProgrammingExercisesWithProperSearchTerm() throws Exception {
        programmingExerciseUtilService.addEnrolledCourseWithNamedProgrammingExerciseAndTestCases("Java JDK13", TEST_PREFIX);
        programmingExerciseUtilService.addEnrolledCourseWithNamedProgrammingExerciseAndTestCases("Python", TEST_PREFIX);
        programmingExerciseUtilService.addEnrolledCourseWithNamedProgrammingExerciseAndTestCases("Java JDK12", TEST_PREFIX);
        final var searchPython = pageableSearchUtilService.configureSearch("Python");
        final var resultPython = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExerciseListItemDTO.class, pageableSearchUtilService.searchMapping(searchPython));
        assertThat(resultPython.getResultsOnPage()).hasSize(1);

        final var searchJava = pageableSearchUtilService.configureSearch("Java");
        final var resultJava = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExerciseListItemDTO.class, pageableSearchUtilService.searchMapping(searchJava));
        assertThat(resultJava.getResultsOnPage()).hasSize(2);

        final var searchSwift = pageableSearchUtilService.configureSearch("Swift");
        final var resultSwift = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExerciseListItemDTO.class, pageableSearchUtilService.searchMapping(searchSwift));
        assertThat(resultSwift.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminGetsResultsFromAllCourses() throws Exception {
        // Use unique name for exercise to not query exercises from other tests
        var title = "testAdminGetsResultsFromAllCourses-Programming";
        programmingExercise.setTitle(title);
        programmingExerciseRepository.save(programmingExercise);

        var otherCourse = courseUtilService.addCourseWithExercise("Programming");
        var otherProgrammingExercise = ExerciseUtilService.getFirstExerciseWithType(otherCourse, ProgrammingExercise.class);
        otherProgrammingExercise.setTitle(title);
        programmingExerciseRepository.save(otherProgrammingExercise);

        final var search = pageableSearchUtilService.configureSearch(title);
        final var result = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExerciseListItemDTO.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSearchResultsCarryTheImportTableFieldSet() throws Exception {
        var title = "importTableFieldSet-Programming";
        programmingExercise.setTitle(title);
        programmingExerciseRepository.save(programmingExercise);
        var examExercise = programmingExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneProgrammingExercise(title + "Exam", "IMPTBLEX", false, TEST_PREFIX);

        final var search = pageableSearchUtilService.configureSearch(title);
        final var result = request.getSearchResult(BASE_RESOURCE, HttpStatus.OK, ProgrammingExerciseListItemDTO.class, pageableSearchUtilService.searchMapping(search));

        var courseItem = result.getResultsOnPage().stream().filter(item -> programmingExercise.getId().equals(item.id())).findFirst().orElseThrow();
        assertThat(courseItem.type()).isEqualTo("programming");
        assertThat(courseItem.title()).isEqualTo(title);
        assertThat(courseItem.programmingLanguage()).isEqualTo(programmingExercise.getProgrammingLanguage());
        // The import table shows no exam checkmark and falls back to course.title for a course exercise.
        assertThat(courseItem.exerciseGroup()).isNull();
        assertThat(courseItem.course()).isNotNull();
        assertThat(courseItem.course().title()).isEqualTo(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getTitle());

        var examItem = result.getResultsOnPage().stream().filter(item -> examExercise.getId().equals(item.id())).findFirst().orElseThrow();
        // The exam checkmark is derived from the presence of exerciseGroup, the course column from exam.course.title.
        assertThat(examItem.exerciseGroup()).isNotNull();
        assertThat(examItem.exerciseGroup().exam()).isNotNull();
        assertThat(examItem.exerciseGroup().exam().course()).isNotNull();
        assertThat(examItem.exerciseGroup().exam().course().title()).isEqualTo(examExercise.getExerciseGroup().getExam().getCourse().getTitle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testNoBuildPlanAccessSecretForImportedExercise() {
        var importedExercise = programmingExerciseImportBasicService.importProgrammingExerciseBasis(programmingExercise, createToBeImported());
        assertThat(programmingExercise.getBuildConfig().getBuildPlanAccessSecret()).isEqualTo(importedExercise.getBuildConfig().getBuildPlanAccessSecret()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDifferentBuildPlanAccessSecretForImportedExercise() {
        programmingExerciseUtilService.addBuildPlanAndSecretToProgrammingExercise(programmingExercise, "text");
        var importedExercise = programmingExerciseImportBasicService.importProgrammingExerciseBasis(programmingExercise, createToBeImported());
        assertThat(programmingExercise.getBuildConfig().getBuildPlanAccessSecret()).isNotNull().isNotEqualTo(importedExercise.getBuildConfig().getBuildPlanAccessSecret());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void findForCreationById_assemblesTheCompleteGraphFromItsSeparateQueries() {
        // findForCreationById combines three queries: the main graph plus one each for the grading criteria (with their
        // structured instructions) and the competency links, which would otherwise multiply the main query's result set.
        // Creation and import return its result without an open session, so every part has to be initialized here -
        // dropping one of the extra lookups makes the corresponding assertion fail with a LazyInitializationException.
        gradingCriterionRepository.saveAll(exerciseUtilService.addGradingInstructionsToExercise(programmingExercise));
        var expectedCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(programmingExercise.getId());
        assertThat(expectedCriteria).as("precondition: the exercise has grading criteria").isNotEmpty();
        assertThat(expectedCriteria).as("precondition: the criteria carry structured instructions").anyMatch(criterion -> !criterion.getStructuredGradingInstructions().isEmpty());

        var loaded = programmingExerciseRepository.findForCreationByIdElseThrow(programmingExercise.getId());

        assertThat(loaded.getGradingCriteria()).hasSameSizeAs(expectedCriteria);
        assertThat(loaded.getGradingCriteria()).as("the nested grading instructions are initialized as well")
                .anyMatch(criterion -> !criterion.getStructuredGradingInstructions().isEmpty());
        // The Atlas competency import adds to this collection after the import returns, so it must be readable.
        assertThat(loaded.getCompetencyLinks()).isNotNull();
        // The main graph is still part of the same result.
        assertThat(loaded.getBuildConfig()).isNotNull();
        assertThat(loaded.getTemplateParticipation()).isNotNull();
        assertThat(loaded.getSolutionParticipation()).isNotNull();
        assertThat(loaded.getCategories()).isNotNull();
        assertThat(loaded.getAuxiliaryRepositories()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExerciseBasis_doesNotRollBackWhenItFailsPartway() {
        // The import deliberately runs without a surrounding transaction, so a failure partway does NOT roll back what was
        // already written - the caller (e.g. ExamImportService) reports such an exercise as incomplete instead. Pin that
        // documented trade-off: re-introducing @Transactional would roll the exercise back and fail this test, forcing a
        // conscious decision rather than a silent behavior change.
        doThrow(new RuntimeException("simulated failure while setting up the solution participation")).when(programmingExerciseParticipationService)
                .setupInitialSolutionParticipation(any());

        final var toBeImported = createToBeImported();
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> programmingExerciseImportBasicService.importProgrammingExerciseBasis(programmingExercise, toBeImported))
                .withMessageContaining("simulated failure");

        // The exercise is persisted before the participations are set up, so it survives the failure.
        assertThat(toBeImported.getId()).as("the new exercise was persisted before the failure").isNotNull();
        assertThat(programmingExerciseRepository.findById(toBeImported.getId())).as("the partially imported exercise is not rolled back").isPresent();
        // The source exercise must not be affected by the failed import.
        assertThat(programmingExerciseRepository.findById(programmingExercise.getId())).isPresent();
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
