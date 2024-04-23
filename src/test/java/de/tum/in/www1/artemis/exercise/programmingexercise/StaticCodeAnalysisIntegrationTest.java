package de.tum.in.www1.artemis.exercise.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.config.StaticCodeAnalysisConfigurer;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.StaticCodeAnalysisCategory;
import de.tum.in.www1.artemis.domain.enumeration.CategoryState;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.in.www1.artemis.service.StaticCodeAnalysisService;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.StaticCodeAnalysisResource;

class StaticCodeAnalysisIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "staticcodeanalysis";

    @Autowired
    private StaticCodeAnalysisService staticCodeAnalysisService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private ProgrammingExercise programmingExerciseSCAEnabled;

    private ProgrammingExercise programmingExercise;

    private Course course;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        programmingExerciseSCAEnabled = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        course = courseRepository.findWithEagerExercisesById(programmingExerciseSCAEnabled.getCourseViaExerciseGroupOrCourseMember().getId());
        var tempProgrammingEx = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now(), ZonedDateTime.now().plusDays(1),
                programmingExerciseSCAEnabled.getCourseViaExerciseGroupOrCourseMember());
        programmingExercise = programmingExerciseRepository.save(tempProgrammingEx);
    }

    private String parameterizeEndpoint(String endpoint, ProgrammingExercise exercise) {
        return endpoint.replace("{exerciseId}", String.valueOf(exercise.getId()));
    }

    @Test
    void testCreateDefaultCategories_noConfigurationAvailable() {
        // Haskell does not have a default configuration at the time of creation of this test
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.HASKELL);
        staticCodeAnalysisService.createDefaultCategories(programmingExercise);
        var categories = staticCodeAnalysisCategoryRepository.findByExerciseId(programmingExercise.getId());
        assertThat(categories).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetStaticCodeAnalysisCategories() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        var categories = request.get(endpoint, HttpStatus.OK, new TypeReference<Set<StaticCodeAnalysisCategory>>() {
        });
        assertThat(programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("exercise")
                .containsExactlyInAnyOrderElementsOf(categories);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "SWIFT", "C" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateDefaultCategories(ProgrammingLanguage programmingLanguage) {
        var testExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now(), ZonedDateTime.now().plusDays(1),
                programmingExerciseSCAEnabled.getCourseViaExerciseGroupOrCourseMember(), programmingLanguage);
        testExercise = programmingExerciseRepository.save(testExercise);
        staticCodeAnalysisService.createDefaultCategories(testExercise);
        // Swift has only one default category at the time of creation of this test
        var categories = staticCodeAnalysisCategoryRepository.findByExerciseId(testExercise.getId());
        if (programmingLanguage == ProgrammingLanguage.SWIFT) {
            assertThat(categories).hasSize(6);
            assertThat(categories.stream().filter(c -> c.getState() == CategoryState.FEEDBACK).count()).isEqualTo(1);
        }
        else if (programmingLanguage == ProgrammingLanguage.JAVA) {
            assertThat(categories).hasSize(11);
            assertThat(categories.stream().filter(c -> c.getState() == CategoryState.FEEDBACK).count()).isEqualTo(7);
        }
        else if (programmingLanguage == ProgrammingLanguage.C) {
            assertThat(categories).hasSize(5);
            assertThat(categories.stream().filter(c -> c.getState() == CategoryState.FEEDBACK).count()).isEqualTo(4);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testGetStaticCodeAnalysisCategories_asStudent_forbidden() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        request.getList(endpoint, HttpStatus.FORBIDDEN, StaticCodeAnalysisCategory.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "other-ta1", roles = "TA")
    void testGetStaticCodeAnalysisCategories_notAtLeastTAInCourse_forbidden() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        userUtilService.addTeachingAssistant("other-tas", TEST_PREFIX + "other-ta");
        request.getList(endpoint, HttpStatus.FORBIDDEN, StaticCodeAnalysisCategory.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetStaticCodeAnalysisCategories_staticCodeAnalysisNotEnabled_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExercise);
        request.getList(endpoint, HttpStatus.BAD_REQUEST, StaticCodeAnalysisCategory.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "SWIFT", "C" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories(ProgrammingLanguage programmingLanguage) throws Exception {
        var programmingExSCAEnabled = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories(programmingLanguage);
        ProgrammingExercise exerciseWithSolutionParticipation = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExSCAEnabled.getId()).orElseThrow();
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExSCAEnabled);
        // Change the first category
        var categoryIterator = programmingExSCAEnabled.getStaticCodeAnalysisCategories().iterator();
        var firstCategory = categoryIterator.next();
        firstCategory.setState(CategoryState.GRADED);
        firstCategory.setPenalty(33D);
        firstCategory.setMaxPenalty(44D);
        // Remove the second category
        var removedCategory = categoryIterator.next();
        categoryIterator.remove();

        var responseCategories = request.patchWithResponseBody(endpoint, programmingExSCAEnabled.getStaticCodeAnalysisCategories(),
                new TypeReference<List<StaticCodeAnalysisCategory>>() {
                }, HttpStatus.OK);
        var savedCategories = staticCodeAnalysisCategoryRepository.findByExerciseId(programmingExSCAEnabled.getId());

        // The removed category should not be deleted
        programmingExSCAEnabled.getStaticCodeAnalysisCategories().add(removedCategory);
        assertThat(responseCategories).usingRecursiveFieldByFieldElementComparatorIgnoringFields("exercise").containsExactlyInAnyOrderElementsOf(savedCategories);
        assertThat(responseCategories).usingRecursiveFieldByFieldElementComparatorIgnoringFields("exercise")
                .containsExactlyInAnyOrderElementsOf(programmingExSCAEnabled.getStaticCodeAnalysisCategories());
        assertThat(savedCategories).usingRecursiveFieldByFieldElementComparatorIgnoringFields("exercise")
                .containsExactlyInAnyOrderElementsOf(programmingExSCAEnabled.getStaticCodeAnalysisCategories());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetCategories_staticCodeAnalysisNotEnabled_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.RESET, programmingExercise);
        request.patch(endpoint, "{}", HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "other-instructor1", roles = "INSTRUCTOR")
    void testResetCategories_instructorInWrongCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", TEST_PREFIX + "other-instructor");
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.RESET, programmingExerciseSCAEnabled);
        request.patch(endpoint, "{}", HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "SWIFT", "C" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetCategories(ProgrammingLanguage programmingLanguage) throws Exception {
        // Create a programming exercise with real categories
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(true, false, programmingLanguage);
        ProgrammingExercise exercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(course.getExercises().iterator().next().getId()).orElseThrow();
        staticCodeAnalysisService.createDefaultCategories(exercise);
        var originalCategories = staticCodeAnalysisCategoryRepository.findByExerciseId(exercise.getId());

        // Alter the categories
        var alteredCategories = staticCodeAnalysisCategoryRepository.findByExerciseId(exercise.getId()).stream().peek(category -> {
            category.setPenalty(5D);
            category.setMaxPenalty(15D);
            category.setState(CategoryState.GRADED);
        }).toList();
        staticCodeAnalysisCategoryRepository.saveAll(alteredCategories);

        // Perform the request and assert that the original state was restored
        final var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.RESET, exercise);
        final var categoriesResponse = request.patchWithResponseBody(endpoint, "{}", new TypeReference<Set<StaticCodeAnalysisCategory>>() {
        }, HttpStatus.OK);
        final Set<StaticCodeAnalysisCategory> categoriesInDB = staticCodeAnalysisCategoryRepository.findByExerciseId(exercise.getId());
        final Set<StaticCodeAnalysisCategory> expectedCategories = StaticCodeAnalysisConfigurer.staticCodeAnalysisConfiguration().get(exercise.getProgrammingLanguage()).stream()
                .map(c -> c.toStaticCodeAnalysisCategory(exercise)).collect(Collectors.toSet());

        assertThat(categoriesResponse).usingRecursiveFieldByFieldElementComparatorIgnoringFields("exercise").containsExactlyInAnyOrderElementsOf(categoriesInDB);
        assertThat(categoriesInDB).usingRecursiveFieldByFieldElementComparatorIgnoringFields("exercise").containsExactlyInAnyOrderElementsOf(originalCategories);
        assertThat(categoriesInDB).usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "exercise").containsExactlyInAnyOrderElementsOf(expectedCategories);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testUpdateStaticCodeAnalysisCategories_asStudent_forbidden() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "other-ta1", roles = "TA")
    void testUpdateStaticCodeAnalysisCategories_notAtLeastTAInCourse_forbidden() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        userUtilService.addTeachingAssistant("other-tas", TEST_PREFIX + "other-ta");
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_staticCodeAnalysisNotEnabled_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExercise);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_categoryIdMissing_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next().setId(null);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_penaltyNullOrNegative_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next().setPenalty(null);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next().setPenalty(-1D);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_maxPenaltySmallerThanPenalty_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        var category = programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next();
        category.setMaxPenalty(3D);
        category.setPenalty(5D);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_stateIsNull_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next().setState(null);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_exerciseIdsDoNotMatch_conflict() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next().getExercise().setId(1234L);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.CONFLICT);
    }

    @Test
    void testDeletionOfStaticCodeAnalysisCategoriesOnExerciseDeletion() {
        programmingExerciseRepository.delete(programmingExerciseSCAEnabled);
        var categories = staticCodeAnalysisCategoryRepository.findByExerciseId(programmingExerciseSCAEnabled.getId());
        assertThat(categories).isEmpty();
    }

    @Test
    void shouldRemoveFeedbackOfInactiveCategories() {
        var result = new Result();
        var feedbackForInactiveCategory = ProgrammingExerciseFactory.createSCAFeedbackWithInactiveCategory(result);
        result.addFeedback(feedbackForInactiveCategory);
        var filteredFeedback = staticCodeAnalysisCategoryRepository.categorizeScaFeedback(result, List.of(feedbackForInactiveCategory), programmingExerciseSCAEnabled);
        assertThat(filteredFeedback).isEmpty();
        assertThat(result.getFeedbacks()).isEmpty();
    }

    @Test
    void shouldCategorizeFeedback() throws JsonProcessingException {
        var result = new Result();
        var feedback = new Feedback().result(result).text(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER).reference("SPOTBUGS").detailText("{\"category\": \"BAD_PRACTICE\"}")
                .type(FeedbackType.AUTOMATIC).positive(false);
        result.addFeedback(feedback);
        var filteredFeedback = staticCodeAnalysisCategoryRepository.categorizeScaFeedback(result, List.of(feedback), programmingExerciseSCAEnabled);
        assertThat(filteredFeedback).hasSize(1);
        assertThat(result.getFeedbacks()).containsExactlyInAnyOrderElementsOf(filteredFeedback);
        assertThat(result.getFeedbacks().getFirst().getStaticCodeAnalysisCategory()).isEqualTo("Bad Practice");
        assertThat(new ObjectMapper().readValue(result.getFeedbacks().getFirst().getDetailText(), StaticCodeAnalysisReportDTO.StaticCodeAnalysisIssue.class).getPenalty())
                .isEqualTo(3.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testImportCategories() throws Exception {
        ProgrammingExercise sourceExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, true);
        staticCodeAnalysisService.createDefaultCategories(sourceExercise);

        var categories = staticCodeAnalysisCategoryRepository.findByExerciseId(sourceExercise.getId());
        for (var category : categories) {
            category.setState(CategoryState.GRADED);
            category.setMaxPenalty(10.0);
            category.setPenalty(5.0);
        }

        staticCodeAnalysisCategoryRepository.saveAll(categories);

        ProgrammingExercise exerciseWithSolutionParticipation = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseSCAEnabled.getId()).orElseThrow();

        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.IMPORT, programmingExerciseSCAEnabled);
        var newCategories = request.patchWithResponseBodyList(endpoint + "?sourceExerciseId=" + sourceExercise.getId(), null, StaticCodeAnalysisCategory.class, HttpStatus.OK);

        assertThat(newCategories).hasSameSizeAs(categories).usingRecursiveFieldByFieldElementComparatorIgnoringFields("exercise", "id").containsAll(categories);
        assertThat(newCategories).allSatisfy(category -> assertThat(category.getExercise().getId()).isEqualTo(programmingExerciseSCAEnabled.getId()));

        var savedCategories = staticCodeAnalysisCategoryRepository.findByExerciseId(programmingExerciseSCAEnabled.getId());
        assertThat(savedCategories).hasSameSizeAs(newCategories).containsAll(newCategories);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testImportCategories_noSCASource() throws Exception {
        ProgrammingExercise sourceExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);

        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.IMPORT, programmingExerciseSCAEnabled);
        request.patch(endpoint + "?sourceExerciseId=" + sourceExercise.getId(), null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testImportCategories_noSCATarget() throws Exception {
        ProgrammingExercise sourceExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, true);
        ProgrammingExercise targetExerciseNoSCA = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, false);

        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.IMPORT, targetExerciseNoSCA);
        request.patch(endpoint + "?sourceExerciseId=" + sourceExercise.getId(), null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "EDITOR")
    void testImportCategories_asTutor() throws Exception {
        ProgrammingExercise sourceExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);

        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.IMPORT, programmingExerciseSCAEnabled);
        request.patch(endpoint + "?sourceExerciseId=" + sourceExercise.getId(), null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testImportCategories_differentLanguages() throws Exception {
        ProgrammingExercise sourceExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, true, false, ProgrammingLanguage.SWIFT);

        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.IMPORT, programmingExerciseSCAEnabled);
        request.patch(endpoint + "?sourceExerciseId=" + sourceExercise.getId(), null, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testImportCategories_asEditor_wrongCourse() throws Exception {
        Course otherCourse = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(true);
        otherCourse.setEditorGroupName("otherEditorGroup");
        otherCourse.setInstructorGroupName("otherInstructorGroup");
        courseRepository.save(otherCourse);
        Exercise sourceExercise = otherCourse.getExercises().iterator().next();

        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.IMPORT, programmingExerciseSCAEnabled);
        request.patch(endpoint + "?sourceExerciseId=" + sourceExercise.getId(), null, HttpStatus.FORBIDDEN);
    }
}
