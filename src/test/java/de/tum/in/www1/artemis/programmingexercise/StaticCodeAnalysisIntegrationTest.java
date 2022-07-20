package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.CategoryState;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.in.www1.artemis.service.StaticCodeAnalysisService;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.StaticCodeAnalysisResource;

class StaticCodeAnalysisIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private StaticCodeAnalysisService staticCodeAnalysisService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    @Qualifier("staticCodeAnalysisConfiguration")
    private Map<ProgrammingLanguage, List<StaticCodeAnalysisDefaultCategory>> staticCodeAnalysisDefaultConfigurations;

    @Autowired
    private StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    private ProgrammingExercise programmingExerciseSCAEnabled;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void initTestCase() {
        database.addUsers(2, 1, 0, 1);
        programmingExerciseSCAEnabled = database.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        var tempProgrammingEx = ModelFactory.generateProgrammingExercise(ZonedDateTime.now(), ZonedDateTime.now().plusDays(1),
                programmingExerciseSCAEnabled.getCourseViaExerciseGroupOrCourseMember());
        programmingExercise = programmingExerciseRepository.save(tempProgrammingEx);
        bambooRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
        bambooRequestMockProvider.reset();
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetStaticCodeAnalysisCategories() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        var categories = request.get(endpoint, HttpStatus.OK, new TypeReference<Set<StaticCodeAnalysisCategory>>() {
        });
        assertThat(programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories()).usingRecursiveFieldByFieldElementComparator().usingElementComparatorIgnoringFields("exercise")
                .containsExactlyInAnyOrderElementsOf(categories);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "SWIFT", "C" })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCreateDefaultCategories(ProgrammingLanguage programmingLanguage) {
        var testExercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now(), ZonedDateTime.now().plusDays(1),
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
    @WithMockUser(username = "student1", roles = "STUDENT")
    void testGetStaticCodeAnalysisCategories_asStudent_forbidden() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        request.getList(endpoint, HttpStatus.FORBIDDEN, StaticCodeAnalysisCategory.class);
    }

    @Test
    @WithMockUser(username = "other-ta1", roles = "TA")
    void testGetStaticCodeAnalysisCategories_notAtLeastTAInCourse_forbidden() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        database.addTeachingAssistant("other-tas", "other-ta");
        request.getList(endpoint, HttpStatus.FORBIDDEN, StaticCodeAnalysisCategory.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetStaticCodeAnalysisCategories_staticCodeAnalysisNotEnabled_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExercise);
        request.getList(endpoint, HttpStatus.BAD_REQUEST, StaticCodeAnalysisCategory.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "SWIFT", "C" })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories(ProgrammingLanguage programmingLanguage) throws Exception {
        var programmingExSCAEnabled = database.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories(programmingLanguage);
        ProgrammingExercise exerciseWithSolutionParticipation = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExSCAEnabled.getId()).get();
        bambooRequestMockProvider.mockTriggerBuild(exerciseWithSolutionParticipation.getSolutionParticipation());
        bambooRequestMockProvider.mockTriggerBuild(exerciseWithSolutionParticipation.getTemplateParticipation());
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
        assertThat(responseCategories).usingRecursiveFieldByFieldElementComparator().usingElementComparatorIgnoringFields("exercise")
                .containsExactlyInAnyOrderElementsOf(savedCategories);
        assertThat(responseCategories).usingRecursiveFieldByFieldElementComparator().usingElementComparatorIgnoringFields("exercise")
                .containsExactlyInAnyOrderElementsOf(programmingExSCAEnabled.getStaticCodeAnalysisCategories());
        assertThat(savedCategories).usingRecursiveFieldByFieldElementComparator().usingElementComparatorIgnoringFields("exercise")
                .containsExactlyInAnyOrderElementsOf(programmingExSCAEnabled.getStaticCodeAnalysisCategories());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testResetCategories_staticCodeAnalysisNotEnabled_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.RESET, programmingExercise);
        request.patch(endpoint, "{}", HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "other-instructor1", roles = "INSTRUCTOR")
    public void testResetCategories_instructorInWrongCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "other-instructor");
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.RESET, programmingExerciseSCAEnabled);
        request.patch(endpoint, "{}", HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "SWIFT", "C" })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testResetCategories(ProgrammingLanguage programmingLanguage) throws Exception {
        // Create a programming exercise with real categories
        var course = database.addCourseWithOneProgrammingExercise(true, false, programmingLanguage);
        ProgrammingExercise exercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(course.getExercises().iterator().next().getId()).get();
        bambooRequestMockProvider.mockTriggerBuild(exercise.getSolutionParticipation());
        bambooRequestMockProvider.mockTriggerBuild(exercise.getTemplateParticipation());
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
        final var categoriesInDB = staticCodeAnalysisCategoryRepository.findByExerciseId(exercise.getId());

        assertThat(categoriesResponse).usingElementComparatorIgnoringFields("exercise").containsExactlyInAnyOrderElementsOf(categoriesInDB);
        assertThat(categoriesInDB).usingElementComparatorIgnoringFields("exercise").containsExactlyInAnyOrderElementsOf(originalCategories);
        assertThat(categoriesInDB).usingElementComparatorIgnoringFields("id", "exercise").isEqualTo(staticCodeAnalysisDefaultConfigurations.get(exercise.getProgrammingLanguage()));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void testUpdateStaticCodeAnalysisCategories_asStudent_forbidden() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "other-ta1", roles = "TA")
    void testUpdateStaticCodeAnalysisCategories_notAtLeastTAInCourse_forbidden() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        database.addTeachingAssistant("other-tas", "other-ta");
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_staticCodeAnalysisNotEnabled_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExercise);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_categoryIdMissing_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next().setId(null);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_penaltyNullOrNegative_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next().setPenalty(null);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next().setPenalty(-1D);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_maxPenaltySmallerThanPenalty_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        var category = programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next();
        category.setMaxPenalty(3D);
        category.setPenalty(5D);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_stateIsNull_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next().setState(null);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
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
        var feedbackForInactiveCategory = ModelFactory.createSCAFeedbackWithInactiveCategory(result);
        result.addFeedback(feedbackForInactiveCategory);
        var filteredFeedback = staticCodeAnalysisService.categorizeScaFeedback(result, List.of(feedbackForInactiveCategory), programmingExerciseSCAEnabled);
        assertThat(filteredFeedback).isEmpty();
        assertThat(result.getFeedbacks()).isEmpty();
    }

    @Test
    void shouldCategorizeFeedback() throws JsonProcessingException {
        var result = new Result();
        var feedback = new Feedback().result(result).text(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER).reference("SPOTBUGS").detailText("{\"category\": \"BAD_PRACTICE\"}")
                .type(FeedbackType.AUTOMATIC).positive(false);
        result.addFeedback(feedback);
        var filteredFeedback = staticCodeAnalysisService.categorizeScaFeedback(result, List.of(feedback), programmingExerciseSCAEnabled);
        assertThat(filteredFeedback).hasSize(1);
        assertThat(result.getFeedbacks()).containsExactlyInAnyOrderElementsOf(filteredFeedback);
        assertThat(result.getFeedbacks().iterator().next().getStaticCodeAnalysisCategory()).isEqualTo("Bad Practice");
        assertThat(new ObjectMapper().readValue(result.getFeedbacks().iterator().next().getDetailText(), StaticCodeAnalysisReportDTO.StaticCodeAnalysisIssue.class).getPenalty())
                .isEqualTo(3.0);
    }
}
