package de.tum.in.www1.artemis.exercise.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints;

class ProgrammingExerciseTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "programmingexercise";

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository participationRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    private Long programmingExerciseId;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 2, 2, 0, 2);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExerciseId = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class).getId();
    }

    void updateProgrammingExercise(ProgrammingExercise programmingExercise, String newProblem, String newTitle) throws Exception {
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();
        programmingExercise.setProblemStatement(newProblem);
        programmingExercise.setTitle(newTitle);

        bambooRequestMockProvider.mockBuildPlanExists(programmingExercise.getTemplateBuildPlanId(), true, false);
        bambooRequestMockProvider.mockBuildPlanExists(programmingExercise.getSolutionBuildPlanId(), true, false);
        bitbucketRequestMockProvider.mockRepositoryUrlIsValid(programmingExercise.getVcsTemplateRepositoryUrl(), programmingExercise.getProjectKey(), true);
        bitbucketRequestMockProvider.mockRepositoryUrlIsValid(programmingExercise.getVcsSolutionRepositoryUrl(), programmingExercise.getProjectKey(), true);

        var programmingExerciseCountBefore = programmingExerciseRepository.count();

        ProgrammingExercise updatedProgrammingExercise = request.putWithResponseBody("/api/programming-exercises", programmingExercise, ProgrammingExercise.class, HttpStatus.OK);

        // The result from the put response should be updated with the new data.
        assertThat(updatedProgrammingExercise.getProblemStatement()).isEqualTo(newProblem);
        assertThat(updatedProgrammingExercise.getTitle()).isEqualTo(newTitle);

        // There should still be the same number of programming exercises.
        assertThat(programmingExerciseRepository.count()).isEqualTo(programmingExerciseCountBefore);
        // The programming exercise in the db should also be updated.
        ProgrammingExercise programmingExerciseFromDb = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).get();
        assertThat(programmingExerciseFromDb.getProblemStatement()).isEqualTo(newProblem);
        assertThat(programmingExerciseFromDb.getTitle()).isEqualTo(newTitle);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseOnce() throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId)
                .get();
        updateProgrammingExercise(programmingExercise, "new problem 1", "new title 1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseTwice() throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId)
                .get();
        updateProgrammingExercise(programmingExercise, "new problem 1", "new title 1");
        updateProgrammingExercise(programmingExercise, "new problem 2", "new title 2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProblemStatement() throws Exception {
        final var newProblem = "a new problem statement";
        final var endpoint = "/api" + ProgrammingExerciseResourceEndpoints.PROBLEM.replace("{exerciseId}", String.valueOf(programmingExerciseId));
        ProgrammingExercise updatedProgrammingExercise = request.patchWithResponseBody(endpoint, newProblem, ProgrammingExercise.class, HttpStatus.OK, MediaType.TEXT_PLAIN);

        assertThat(updatedProgrammingExercise.getProblemStatement()).isEqualTo(newProblem);

        ProgrammingExercise fromDb = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId).get();
        assertThat(fromDb.getProblemStatement()).isEqualTo(newProblem);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateExerciseAutomaticFeedbackNoTestCases() throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId)
                .get();

        Set<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).isEmpty();

        // no test cases, changing to automatic feedback: update should work
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        updateProgrammingExercise(programmingExercise, "new problem 1", "new title 1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateExerciseAutomaticFeedbackTestCasesPositiveWeight() throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId)
                .get();
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);

        // test cases with weights > 0, changing to automatic feedback: update should work
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        updateProgrammingExercise(programmingExercise, "new problem 1", "new title 1");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(AssessmentType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateExerciseTestCasesZeroWeight(AssessmentType assessmentType) throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId)
                .get();
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.forEach(testCase -> testCase.setWeight(0D));
        programmingExerciseTestCaseRepository.saveAll(testCases);

        programmingExercise.setAssessmentType(assessmentType);

        if (assessmentType == AssessmentType.AUTOMATIC) {
            bambooRequestMockProvider.enableMockingOfRequests();
            bitbucketRequestMockProvider.enableMockingOfRequests();
            bambooRequestMockProvider.mockBuildPlanExists(programmingExercise.getTemplateBuildPlanId(), true, false);
            bambooRequestMockProvider.mockBuildPlanExists(programmingExercise.getSolutionBuildPlanId(), true, false);
            bitbucketRequestMockProvider.mockRepositoryUrlIsValid(programmingExercise.getVcsTemplateRepositoryUrl(), programmingExercise.getProjectKey(), true);
            bitbucketRequestMockProvider.mockRepositoryUrlIsValid(programmingExercise.getVcsSolutionRepositoryUrl(), programmingExercise.getProjectKey(), true);
        }

        updateProgrammingExercise(programmingExercise, "new problem 1", "new title 1");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void findAppropriateSubmissionRespectingIndividualDueDate(boolean isSubmissionAfterIndividualDueDate) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        exercise.setDueDate(ZonedDateTime.now());
        exercise = programmingExerciseRepository.save(exercise);

        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setType(SubmissionType.OTHER);
        if (isSubmissionAfterIndividualDueDate) {
            submission.setSubmissionDate(ZonedDateTime.now().plusHours(26));
        }
        else {
            // submission time after exercise due date but before individual due date
            submission.setSubmissionDate(ZonedDateTime.now().plusHours(1));
        }
        submission = programmingExerciseUtilService.addProgrammingSubmission(exercise, submission, TEST_PREFIX + "student1");

        ProgrammingExerciseStudentParticipation participation = participationRepository.findByExerciseIdAndStudentLogin(programmingExerciseId, TEST_PREFIX + "student1").get();
        participation.setIndividualDueDate(ZonedDateTime.now().plusDays(1));
        submission.setParticipation(participation);

        Submission latestValidSubmission = exercise.findAppropriateSubmissionByResults(Set.of(submission));
        if (isSubmissionAfterIndividualDueDate) {
            assertThat(latestValidSubmission).isNull();
        }
        else {
            assertThat(latestValidSubmission).isEqualTo(submission);
        }
    }

    @Test
    void testFindRelevantParticipations() {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);

        StudentParticipation gradedParticipationInitialized = new StudentParticipation();
        gradedParticipationInitialized.setInitializationState(InitializationState.INITIALIZED);
        gradedParticipationInitialized.setExercise(exercise);
        StudentParticipation gradedParticipationFinished = new StudentParticipation();
        gradedParticipationFinished.setInitializationState(InitializationState.FINISHED);
        gradedParticipationFinished.setExercise(exercise);
        StudentParticipation practiceParticipation = new StudentParticipation();
        practiceParticipation.setTestRun(true);
        practiceParticipation.setExercise(exercise);
        List<StudentParticipation> allParticipations = List.of(gradedParticipationInitialized, gradedParticipationFinished, practiceParticipation);

        // Create all possible combinations of the entries in allParticipations
        for (int i = 0; i < 2 << allParticipations.size(); i++) {
            List<StudentParticipation> participationsToTest = new ArrayList<>();
            for (int j = 0; j < allParticipations.size(); j++) {
                if (((i >> j) & 1) == 1) {
                    participationsToTest.add(allParticipations.get(j));
                }
            }
            List<StudentParticipation> expectedParticipations = new ArrayList<>(participationsToTest);
            if (expectedParticipations.contains(gradedParticipationInitialized)) {
                expectedParticipations.remove(gradedParticipationFinished);
            }

            List<StudentParticipation> relevantParticipations = exercise.findRelevantParticipation(participationsToTest);
            assertThat(relevantParticipations).containsExactlyElementsOf(expectedParticipations);
        }
    }
}
