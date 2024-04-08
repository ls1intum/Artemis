package de.tum.in.www1.artemis.exercise.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints;

class ProgrammingExerciseTest extends AbstractSpringIntegrationJenkinsGitlabTest {

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

    @Autowired
    private ChannelRepository channelRepository;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 2, 2, 0, 2);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExerciseId = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class).getId();
    }

    void updateProgrammingExercise(ProgrammingExercise programmingExercise, String newProblem, String newTitle) throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
        programmingExercise.setProblemStatement(newProblem);
        programmingExercise.setTitle(newTitle);

        jenkinsRequestMockProvider.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, false);
        jenkinsRequestMockProvider.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getSolutionBuildPlanId(), true, false);
        gitlabRequestMockProvider.mockRepositoryUriIsValid(programmingExercise.getVcsTemplateRepositoryUri(), true);
        gitlabRequestMockProvider.mockRepositoryUriIsValid(programmingExercise.getVcsSolutionRepositoryUri(), true);

        var programmingExerciseCountBefore = programmingExerciseRepository.count();

        ProgrammingExercise updatedProgrammingExercise = request.putWithResponseBody("/api/programming-exercises", programmingExercise, ProgrammingExercise.class, HttpStatus.OK);

        // The result from the put response should be updated with the new data.
        assertThat(updatedProgrammingExercise.getProblemStatement()).isEqualTo(newProblem);
        assertThat(updatedProgrammingExercise.getTitle()).isEqualTo(newTitle);

        // There should still be the same number of programming exercises.
        assertThat(programmingExerciseRepository.count()).isEqualTo(programmingExerciseCountBefore);
        // The programming exercise in the db should also be updated.
        ProgrammingExercise programmingExerciseFromDb = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).orElseThrow();
        assertThat(programmingExerciseFromDb.getProblemStatement()).isEqualTo(newProblem);
        assertThat(programmingExerciseFromDb.getTitle()).isEqualTo(newTitle);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseOnce() throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId)
                .orElseThrow();
        updateProgrammingExercise(programmingExercise, "new problem 1", "new title 1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseTwice() throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId)
                .orElseThrow();
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

        ProgrammingExercise fromDb = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId).orElseThrow();
        assertThat(fromDb.getProblemStatement()).isEqualTo(newProblem);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateExerciseAutomaticFeedbackNoTestCases() throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId)
                .orElseThrow();

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
                .orElseThrow();
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
                .orElseThrow();
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.forEach(testCase -> testCase.setWeight(0D));
        programmingExerciseTestCaseRepository.saveAll(testCases);

        programmingExercise.setAssessmentType(assessmentType);

        if (assessmentType == AssessmentType.AUTOMATIC) {
            jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
            gitlabRequestMockProvider.enableMockingOfRequests();
            jenkinsRequestMockProvider.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, false);
            jenkinsRequestMockProvider.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getSolutionBuildPlanId(), true, false);
            gitlabRequestMockProvider.mockRepositoryUriIsValid(programmingExercise.getVcsTemplateRepositoryUri(), true);
            gitlabRequestMockProvider.mockRepositoryUriIsValid(programmingExercise.getVcsSolutionRepositoryUri(), true);
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

        ProgrammingExerciseStudentParticipation participation = participationRepository.findByExerciseIdAndStudentLogin(programmingExerciseId, TEST_PREFIX + "student1")
                .orElseThrow();
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
        practiceParticipation.setPracticeMode(true);
        practiceParticipation.setExercise(exercise);
        List<StudentParticipation> allParticipations = List.of(gradedParticipationInitialized, gradedParticipationFinished, practiceParticipation);

        // Create all possible combinations of the entries in allParticipations
        for (int i = 0; i < 2 << allParticipations.size(); i++) {
            Set<StudentParticipation> participationsToTest = new HashSet<>();
            for (int j = 0; j < allParticipations.size(); j++) {
                if (((i >> j) & 1) == 1) {
                    participationsToTest.add(allParticipations.get(j));
                }
            }
            Set<StudentParticipation> expectedParticipations = new HashSet<>(participationsToTest);
            if (expectedParticipations.contains(gradedParticipationInitialized)) {
                expectedParticipations.remove(gradedParticipationFinished);
            }

            Set<StudentParticipation> relevantParticipations = exercise.findRelevantParticipation(participationsToTest);
            assertThat(relevantParticipations).containsExactlyElementsOf(expectedParticipations);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteProgrammingExerciseChannel() throws Exception {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        Exercise programmingExercise = course.getExercises().stream().findFirst().orElseThrow();
        Channel exerciseChannel = exerciseUtilService.addChannelToExercise(programmingExercise);

        request.delete("/api/programming-exercises/" + programmingExercise.getId(), HttpStatus.OK);

        Optional<Channel> exerciseChannelAfterDelete = channelRepository.findById(exerciseChannel.getId());
        assertThat(exerciseChannelAfterDelete).isEmpty();
    }
}
