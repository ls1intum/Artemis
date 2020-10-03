package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.type.TypeReference;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.service.ProgrammingExerciseTestCaseService;
import de.tum.in.www1.artemis.service.StaticCodeAnalysisService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.StaticCodeAnalysisResource;

class StaticCodeAnalysisIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    private StaticCodeAnalysisService staticCodeAnalysisService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    StudentParticipationRepository studentParticipationRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    ProgrammingExerciseGradingService gradingService;

    @Autowired
    ProgrammingExerciseTestCaseService testCaseService;

    @Autowired
    ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    private StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    private ProgrammingExercise programmingExerciseSCAEnabled;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void initTestCase() {
        database.addUsers(2, 1, 1);
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
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void testGetStaticCodeAnalysisCategories() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        var categories = request.get(endpoint, HttpStatus.OK, new TypeReference<Set<StaticCodeAnalysisCategory>>() {
        });
        assertThat(programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories()).usingRecursiveFieldByFieldElementComparator().usingElementComparatorIgnoringFields("exercise")
                .containsExactlyInAnyOrderElementsOf(categories);
    }

    @Test
    @WithMockUser(value = "student1", roles = "STUDENT")
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
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void testGetStaticCodeAnalysisCategories_staticCodeAnalysisNotEnabled_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExercise);
        request.getList(endpoint, HttpStatus.BAD_REQUEST, StaticCodeAnalysisCategory.class);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories() throws Exception {
        ProgrammingExercise exerciseWithSolutionParticipation = programmingExerciseRepository
                .findWithTemplateParticipationAndSolutionParticipationById(programmingExerciseSCAEnabled.getId()).get();
        bambooRequestMockProvider.mockTriggerBuild(exerciseWithSolutionParticipation.getSolutionParticipation());
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        // Change the first category
        var categoryIterator = programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator();
        var firstCategory = categoryIterator.next();
        firstCategory.setState(CategoryState.GRADED);
        firstCategory.setPenalty(33D);
        firstCategory.setMaxPenalty(44D);
        // Remove the second category
        var removedCategory = categoryIterator.next();
        categoryIterator.remove();

        var responseCategories = request.patchWithResponseBody(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(),
                new TypeReference<List<StaticCodeAnalysisCategory>>() {
                }, HttpStatus.OK);
        var savedCategories = staticCodeAnalysisCategoryRepository.findByExerciseId(programmingExerciseSCAEnabled.getId());

        // The removed category should not be deleted
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().add(removedCategory);
        assertThat(responseCategories).usingRecursiveFieldByFieldElementComparator().usingElementComparatorIgnoringFields("exercise")
                .containsExactlyInAnyOrderElementsOf(savedCategories);
        assertThat(responseCategories).usingRecursiveFieldByFieldElementComparator().usingElementComparatorIgnoringFields("exercise")
                .containsExactlyInAnyOrderElementsOf(programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories());
        assertThat(savedCategories).usingRecursiveFieldByFieldElementComparator().usingElementComparatorIgnoringFields("exercise")
                .containsExactlyInAnyOrderElementsOf(programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories());
    }

    @Test
    @WithMockUser(value = "student1", roles = "STUDENT")
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
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_staticCodeAnalysisNotEnabled_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExercise);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_categoryIdMissing_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next().setId(null);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_penaltyNullOrNegative_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next().setPenalty(null);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next().setPenalty(-1D);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_maxPenaltySmallerThanPenalty_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        var category = programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next();
        category.setMaxPenalty(3D);
        category.setPenalty(5D);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_stateIsNull_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next().setState(null);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
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
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void shouldCalculateScoreWithStaticCodeAnalysisPenalties() throws Exception {

        database.addTestCasesToProgrammingExercise(programmingExerciseSCAEnabled);

        // activate test cases
        var testCases = new ArrayList<>(testCaseService.findByExerciseId(programmingExerciseSCAEnabled.getId()));
        testCases.get(0).active(true).afterDueDate(false);
        testCases.get(1).active(true).afterDueDate(false);
        testCases.get(2).active(true).afterDueDate(false);
        testCaseRepository.saveAll(testCases);

        // update categories
        var categories = new ArrayList<>(staticCodeAnalysisService.findByExerciseId(programmingExerciseSCAEnabled.getId()));
        categories.get(0).setName("Bad Practice");
        categories.get(0).setState(CategoryState.GRADED);
        categories.get(0).setPenalty(3D);
        categories.get(1).setName("Code Style");
        categories.get(1).setState(CategoryState.GRADED);
        categories.get(1).setPenalty(5D);
        categories.get(2).setName("Miscellaneous");
        categories.get(2).setState(CategoryState.INACTIVE);
        staticCodeAnalysisCategoryRepository.saveAll(categories);

        // create results
        var participation1 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student1");
        {
            // score 50 %
            var result1 = new Result().participation(participation1).resultString("x of y passed").successful(false).rated(true).score(100L);
            participation1.setResults(Set.of(result1));
            updateAndSaveAutomaticResult(result1, true, false, false, 0, 1);
        }
        var participation2 = database.addStudentParticipationForProgrammingExercise(programmingExerciseSCAEnabled, "student2");
        {
            // score 75 %
            var result2 = new Result().participation(participation2).resultString("x of y passed").successful(false).rated(true).score(100L);
            participation2.setResults(Set.of(result2));
            updateAndSaveAutomaticResult(result2, true, false, true, 2, 1);
        }

        // check results
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(participation1.getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 4L, "1 of 3 passed", true, 4, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }
        {
            var participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(participation2.getId()).get();
            var results = participation.getResults();
            assertThat(results).hasSize(1);
            var singleResult = results.iterator().next();
            testParticipationResult(singleResult, 46L, "2 of 3 passed", true, 6, AssessmentType.AUTOMATIC);
            assertThat(singleResult).isEqualTo(participation.findLatestResult());
        }

    }

    private void testParticipationResult(Result result, long score, String resultString, boolean hasFeedback, int feedbackSize, AssessmentType assessmentType) {
        assertThat(result.getScore()).isEqualTo(score);
        assertThat(result.getResultString()).isEqualTo(resultString);
        assertThat(result.getHasFeedback()).isEqualTo(hasFeedback);
        assertThat(result.getFeedbacks()).hasSize(feedbackSize);
        assertThat(result.getAssessmentType()).isEqualTo(assessmentType);
    }

    private Result updateAndSaveAutomaticResult(Result result, boolean test1Passes, boolean test2Passes, boolean test3Passes, int issuesCategory1, int issuesCategory2) {
        result.addFeedback(new Feedback().result(result).text("test1").positive(test1Passes).type(FeedbackType.AUTOMATIC));
        result.addFeedback(new Feedback().result(result).text("test2").positive(test2Passes).type(FeedbackType.AUTOMATIC));
        result.addFeedback(new Feedback().result(result).text("test3").positive(test3Passes).type(FeedbackType.AUTOMATIC));

        for (int i = 0; i < issuesCategory1; i++) {
            result.addFeedback(new Feedback().result(result).text(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER).reference("SPOTBUGS")
                    .detailText("{\"category\": \"BAD_PRACTICE\"}").type(FeedbackType.AUTOMATIC));
        }
        for (int i = 0; i < issuesCategory2; i++) {
            result.addFeedback(new Feedback().result(result).text(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER).reference("SPOTBUGS").detailText("{\"category\": \"STYLE\"}")
                    .type(FeedbackType.AUTOMATIC));
        }

        result.addFeedback(new Feedback().result(result).text(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER).reference("CHECKSTYLE")
                .detailText("{\"category\": \"miscellaneous\"}").type(FeedbackType.AUTOMATIC));

        result.rated(true) //
                .hasFeedback(true) //
                .successful(test1Passes && test2Passes && test3Passes) //
                .completionDate(ZonedDateTime.now()) //
                .assessmentType(AssessmentType.AUTOMATIC);

        gradingService.updateResult(result, programmingExerciseSCAEnabled, true);

        return resultRepository.save(result);
    }

}
