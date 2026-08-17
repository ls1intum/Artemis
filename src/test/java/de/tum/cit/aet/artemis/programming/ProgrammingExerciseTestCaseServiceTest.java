package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.LongFeedbackTextRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTestCaseDTO;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

class ProgrammingExerciseTestCaseServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "progextestcase";

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private LongFeedbackTextRepository longFeedbackTextRepository;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setUp() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 5, 1, 0, 1);
        var course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExerciseAndTestCases(TEST_PREFIX);
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        RepositoryExportTestUtil.createAndWireBaseRepositories(localVCLocalCITestService, programmingExercise);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        SecurityUtils.setAuthorizationObject();
        programmingExercise = programmingExerciseRepository
                .findByIdWithEagerTestCasesStaticCodeAnalysisCategoriesTemplateAndSolutionParticipationsAndAuxReposAndBuildConfigCategories(programmingExercise.getId())
                .orElseThrow();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldResetCourseExerciseTestCases() {
        testResetTestCases(programmingExercise, Visibility.ALWAYS);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldResetExamExerciseTestCases() {
        programmingExercise.setExerciseGroup(new ExerciseGroup());
        testResetTestCases(programmingExercise, Visibility.AFTER_DUE_DATE);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldCascadeDeleteFeedbackWhenTestCaseIsDeleted() {
        // A fresh, unlinked test case so deleting it exercises only the test_case_feedback -> test_case foreign
        // key, not the task/coverage RESTRICT constraints that a seeded, task-linked test case would also hit.
        ProgrammingExerciseTestCase testCase = testCaseRepository.save(new ProgrammingExerciseTestCase().exercise(programmingExercise).testName("cascadeTest").active(true)
                .weight(1.).bonusMultiplier(1.).bonusPoints(0.).visibility(Visibility.ALWAYS));
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var submission = participationUtilService.addSubmission(participation, new de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission());
        var result = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, null, submission);
        participationUtilService.addTestCaseFeedbackToResult(result, testCase, false, "x".repeat(1500));
        assertThat(testCaseFeedbackRepository.findWithTestCaseByResultIds(List.of(result.getId()))).hasSize(1);

        // test_case_feedback.test_case_id is ON DELETE CASCADE: deleting the test case must remove the
        // referencing feedback row rather than fail on a RESTRICT constraint. The database performs the
        // delete; JPA is not involved. The deduplicated message row intentionally survives (it is shared and
        // garbage-collected by the scheduled cleanup).
        testCaseRepository.deleteById(testCase.getId());

        assertThat(testCaseRepository.findById(testCase.getId())).isEmpty();
        assertThat(testCaseFeedbackRepository.findWithTestCaseByResultIds(List.of(result.getId()))).isEmpty();
    }

    private void testResetTestCases(ProgrammingExercise programmingExercise, Visibility expectedVisibility) {
        participationUtilService.addProgrammingParticipationWithResultForExercise(programmingExercise, TEST_PREFIX + "student1");
        new ArrayList<>(testCaseRepository.findByExerciseId(programmingExercise.getId())).getFirst().weight(50.0);

        assertThat(programmingExercise.getTestCasesChanged()).isFalse();

        testCaseService.reset(programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        ProgrammingExercise updatedProgrammingExercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).orElseThrow();

        for (ProgrammingExerciseTestCase testCase : testCases) {
            assertThat(testCase.getWeight()).isEqualTo(1.0);
            assertThat(testCase.getBonusMultiplier()).isEqualTo(1.0);
            assertThat(testCase.getBonusPoints()).isZero();
            assertThat(testCase.getVisibility()).isEqualTo(expectedVisibility);
        }
        assertThat(updatedProgrammingExercise.getTestCasesChanged()).isTrue();

        verify(groupNotificationService).notifyEditorAndInstructorGroupsAboutChangedTestCasesForProgrammingExercise(updatedProgrammingExercise);
        verify(websocketMessagingService).sendMessage("/topic/programming-exercises/" + programmingExercise.getId() + "/test-cases-changed", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldUpdateTestWeight() {
        participationUtilService.addProgrammingParticipationWithResultForExercise(programmingExercise, TEST_PREFIX + "student1");

        ProgrammingExerciseTestCase testCase = testCaseRepository.findByExerciseId(programmingExercise.getId()).iterator().next();

        Set<ProgrammingExerciseTestCaseDTO> programmingExerciseTestCaseDTOS = new HashSet<>();
        ProgrammingExerciseTestCaseDTO programmingExerciseTestCaseDTO = new ProgrammingExerciseTestCaseDTO(testCase.getId(), 400.0, 1.0, 0.0, Visibility.ALWAYS);
        programmingExerciseTestCaseDTOS.add(programmingExerciseTestCaseDTO);

        assertThat(programmingExercise.getTestCasesChanged()).isFalse();

        testCaseService.update(programmingExercise.getId(), programmingExerciseTestCaseDTOS);

        ProgrammingExercise updatedProgrammingExercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).orElseThrow();

        assertThat(testCaseRepository.findById(testCase.getId()).orElseThrow().getWeight()).isEqualTo(400);
        assertThat(updatedProgrammingExercise.getTestCasesChanged()).isTrue();
        verify(groupNotificationService).notifyEditorAndInstructorGroupsAboutChangedTestCasesForProgrammingExercise(updatedProgrammingExercise);
        verify(websocketMessagingService).sendMessage("/topic/programming-exercises/" + programmingExercise.getId() + "/test-cases-changed", true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(AssessmentType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldAllowTestCaseWeightSumZero(AssessmentType assessmentType) {
        programmingExercise.setAssessmentType(assessmentType);
        programmingExerciseRepository.save(programmingExercise);

        var result = ProgrammingExerciseFactory.generateTestResultDTO(null, "SOLUTION", null, programmingExercise.getProgrammingLanguage(), false,
                List.of("test1", "test2", "test3"), List.of(), null, null, null);
        feedbackCreationService.generateTestCasesFromBuildResult(result, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        Set<ProgrammingExerciseTestCaseDTO> testCaseDTOs = testCases.stream()
                .map(testCase -> new ProgrammingExerciseTestCaseDTO(testCase.getId(), 0.0, testCase.getBonusMultiplier(), testCase.getBonusPoints(), testCase.getVisibility()))
                .collect(Collectors.toSet());
        Set<ProgrammingExerciseTestCase> updated = testCaseService.update(programmingExercise.getId(), testCaseDTOs);
        assertThat(updated).hasSize(3).allMatch(testCase -> testCase.getWeight() == 0.0);
    }
}
