package de.tum.cit.aet.artemis.programming;

import static de.tum.cit.aet.artemis.core.util.RequestUtilService.deleteProgrammingExerciseParamsFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;

class ProgrammingExerciseTest extends AbstractProgrammingIntegrationJenkinsLocalVCTest {

    @Autowired
    private ConversationUtilService conversationUtilService;

    private static final String TEST_PREFIX = "peinttest";

    private Long programmingExerciseId;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 2, 2, 0, 2);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExerciseId = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class).getId();
    }

    void updateProgrammingExercise(ProgrammingExercise programmingExercise, String newProblem, String newTitle) throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests();
        programmingExercise.setProblemStatement(newProblem);
        programmingExercise.setTitle(newTitle);

        jenkinsRequestMockProvider.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, false);
        jenkinsRequestMockProvider.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getSolutionBuildPlanId(), true, false);

        var programmingExerciseCountBefore = programmingExerciseRepository.count();

        ProgrammingExercise updatedProgrammingExercise = request.putWithResponseBody("/api/programming/programming-exercises", programmingExercise, ProgrammingExercise.class,
                HttpStatus.OK);

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
        ProgrammingExercise programmingExercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigById(programmingExerciseId).orElseThrow();
        updateProgrammingExercise(programmingExercise, "new problem 1", "new title 1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseTwice() throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigById(programmingExerciseId).orElseThrow();
        updateProgrammingExercise(programmingExercise, "new problem 1", "new title 1");
        updateProgrammingExercise(programmingExercise, "new problem 2", "new title 2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProblemStatement_courseExercise() throws Exception {
        final var newProblem = "a new problem statement";
        final var endpoint = "/api/programming/programming-exercises/" + programmingExerciseId + "/problem-statement";
        ProgrammingExercise updatedProgrammingExercise = request.patchWithResponseBody(endpoint, newProblem, ProgrammingExercise.class, HttpStatus.OK, MediaType.TEXT_PLAIN);

        assertThat(updatedProgrammingExercise.getProblemStatement()).isEqualTo(newProblem);
        verify(examLiveEventsService, never()).createAndSendProblemStatementUpdateEvent(any(), any(), any());
        verify(groupNotificationScheduleService, timeout(2000).times(1)).checkAndCreateAppropriateNotificationsWhenUpdatingExercise(any(), any(), any());

        ProgrammingExercise fromDb = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId).orElseThrow();
        assertThat(fromDb.getProblemStatement()).isEqualTo(newProblem);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProblemStatement_examExercise() throws Exception {
        var programmingExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise("Testtitle", "TESTEXFOREXAM", true);
        var exam = programmingExercise.getExam();
        StudentExam studentExam = examUtilService.addStudentExam(exam);
        examUtilService.addExerciseToStudentExam(studentExam, programmingExercise);

        final var newProblem = "a new problem statement";
        final var endpoint = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/problem-statement";
        ProgrammingExercise updatedProgrammingExercise = request.patchWithResponseBody(endpoint, newProblem, ProgrammingExercise.class, HttpStatus.OK, MediaType.TEXT_PLAIN);

        assertThat(updatedProgrammingExercise.getProblemStatement()).isEqualTo(newProblem);
        verify(examLiveEventsService, timeout(2000).times(1)).createAndSendProblemStatementUpdateEvent(any(), any(), any());
        verify(groupNotificationScheduleService, never()).checkAndCreateAppropriateNotificationsWhenUpdatingExercise(any(), any(), any());

        ProgrammingExercise fromDb = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId())
                .orElseThrow();
        assertThat(fromDb.getProblemStatement()).isEqualTo(newProblem);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateExerciseAutomaticFeedbackNoTestCases() throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigById(programmingExerciseId).orElseThrow();

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).isEmpty();

        // no test cases, changing to automatic feedback: update should work
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        updateProgrammingExercise(programmingExercise, "new problem 1", "new title 1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateExerciseAutomaticFeedbackTestCasesPositiveWeight() throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigById(programmingExerciseId).orElseThrow();
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);

        // test cases with weights > 0, changing to automatic feedback: update should work
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        updateProgrammingExercise(programmingExercise, "new problem 1", "new title 1");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(AssessmentType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateExerciseTestCasesZeroWeight(AssessmentType assessmentType) throws Exception {
        ProgrammingExercise programmingExercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigById(programmingExerciseId).orElseThrow();
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.forEach(testCase -> testCase.setWeight(0D));
        testCaseRepository.saveAll(testCases);

        programmingExercise.setAssessmentType(assessmentType);

        if (assessmentType == AssessmentType.AUTOMATIC) {
            jenkinsRequestMockProvider.enableMockingOfRequests();
            jenkinsRequestMockProvider.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, false);
            jenkinsRequestMockProvider.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getSolutionBuildPlanId(), true, false);
        }

        updateProgrammingExercise(programmingExercise, "new problem 1", "new title 1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteProgrammingExerciseChannel() throws Exception {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        Exercise programmingExercise = course.getExercises().stream().findFirst().orElseThrow();
        Channel exerciseChannel = conversationUtilService.addChannelToExercise(programmingExercise);

        request.delete("/api/programming/programming-exercises/" + programmingExercise.getId(), HttpStatus.OK, deleteProgrammingExerciseParamsFalse());

        Optional<Channel> exerciseChannelAfterDelete = channelRepository.findById(exerciseChannel.getId());
        assertThat(exerciseChannelAfterDelete).isEmpty();
    }
}
