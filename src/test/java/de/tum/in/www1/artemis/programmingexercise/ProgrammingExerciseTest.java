package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
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
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceErrorKeys;

class ProgrammingExerciseTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository participationRepository;

    private Long programmingExerciseId;

    @BeforeEach
    void init() {
        database.addUsers(2, 2, 0, 2);
        database.addCourseWithOneProgrammingExercise();
        programmingExerciseId = programmingExerciseRepository.findAll().get(0).getId();
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
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

        ProgrammingExercise updatedProgrammingExercise = request.putWithResponseBody("/api/programming-exercises", programmingExercise, ProgrammingExercise.class, HttpStatus.OK);

        // The result from the put response should be updated with the new data.
        assertThat(updatedProgrammingExercise.getProblemStatement()).isEqualTo(newProblem);
        assertThat(updatedProgrammingExercise.getTitle()).isEqualTo(newTitle);

        // There should still be only 1 programming exercise.
        assertThat(programmingExerciseRepository.count()).isEqualTo(1);
        // The programming exercise in the db should also be updated.
        ProgrammingExercise programmingExerciseFromDb = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).get();
        assertThat(programmingExerciseFromDb.getProblemStatement()).isEqualTo(newProblem);
        assertThat(programmingExerciseFromDb.getTitle()).isEqualTo(newTitle);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseOnce() throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId)
                .get();
        updateProgrammingExercise(programmingExercise, "new problem 1", "new title 1");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseTwice() throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId)
                .get();
        updateProgrammingExercise(programmingExercise, "new problem 1", "new title 1");
        updateProgrammingExercise(programmingExercise, "new problem 2", "new title 2");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateProblemStatement() throws Exception {
        final var newProblem = "a new problem statement";
        final var endpoint = "/api" + ProgrammingExerciseResourceEndpoints.PROBLEM.replace("{exerciseId}", String.valueOf(programmingExerciseId));
        ProgrammingExercise updatedProgrammingExercise = request.patchWithResponseBody(endpoint, newProblem, ProgrammingExercise.class, HttpStatus.OK, MediaType.TEXT_PLAIN);

        assertThat(updatedProgrammingExercise.getProblemStatement()).isEqualTo(newProblem);

        ProgrammingExercise fromDb = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId).get();
        assertThat(fromDb.getProblemStatement()).isEqualTo(newProblem);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateExerciseAutomaticFeedbackTestCasesPositiveWeight() throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId)
                .get();
        database.addTestCasesToProgrammingExercise(programmingExercise);

        // test cases with weights > 0, changing to automatic feedback: update should work
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        updateProgrammingExercise(programmingExercise, "new problem 1", "new title 1");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(AssessmentType.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateExerciseTestCasesZeroWeight(AssessmentType assessmentType) throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId)
                .get();
        database.addTestCasesToProgrammingExercise(programmingExercise);

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

            // test cases with weights = 0, changing to automatic feedback: update should NOT work
            request.putAndExpectError("/api/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST, ProgrammingExerciseResourceErrorKeys.INVALID_TEST_CASE_WEIGHTS);
        }
        else {
            // for exercises with manual feedback the update should work
            updateProgrammingExercise(programmingExercise, "new problem 1", "new title 1");
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
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
        submission = database.addProgrammingSubmission(exercise, submission, "student1");

        ProgrammingExerciseStudentParticipation participation = participationRepository.findByExerciseIdAndStudentLogin(programmingExerciseId, "student1").get();
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
}
