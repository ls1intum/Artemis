package de.tum.in.www1.artemis.exercise.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseFeedbackCreationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseTestCaseService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseTestCaseDTO;

class ProgrammingExerciseTestCaseServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "progextestcase";

    @Autowired
    private ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    private ProgrammingExerciseTestCaseService testCaseService;

    @Autowired
    private ProgrammingExerciseFeedbackCreationService feedbackCreationService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 5, 1, 0, 1);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        SecurityUtils.setAuthorizationObject();
        programmingExercise = programmingExerciseRepository
                .findByIdWithEagerTestCasesStaticCodeAnalysisCategoriesHintsAndTemplateAndSolutionParticipationsAndAuxRepos(programmingExercise.getId()).orElseThrow();
        bambooRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() {
        bambooRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldResetCourseExerciseTestCases() throws Exception {
        testResetTestCases(programmingExercise, Visibility.ALWAYS);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldResetExamExerciseTestCases() throws Exception {
        programmingExercise.setExerciseGroup(new ExerciseGroup());
        testResetTestCases(programmingExercise, Visibility.AFTER_DUE_DATE);
    }

    private void testResetTestCases(ProgrammingExercise programmingExercise, Visibility expectedVisibility) throws Exception {
        String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        when(gitService.getLastCommitHash(any())).thenReturn(ObjectId.fromString(dummyHash));
        participationUtilService.addProgrammingParticipationWithResultForExercise(programmingExercise, TEST_PREFIX + "student1");
        new ArrayList<>(testCaseRepository.findByExerciseId(programmingExercise.getId())).get(0).weight(50.0);

        // After a test case reset, the solution and template repository should be built, so the ContinuousIntegrationService needs to be triggered
        bambooRequestMockProvider.mockTriggerBuild(programmingExercise.getSolutionParticipation());
        bambooRequestMockProvider.mockTriggerBuild(programmingExercise.getTemplateParticipation());

        assertThat(programmingExercise.getTestCasesChanged()).isFalse();

        testCaseService.reset(programmingExercise.getId(), programmingExercise.getDefaultTestCaseVisibility());

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
    void shouldUpdateTestWeight() throws Exception {
        // After a test case update, the solution and template repository should be build, so the ContinuousIntegrationService needs to be triggered
        bambooRequestMockProvider.mockTriggerBuild(programmingExercise.getSolutionParticipation());
        bambooRequestMockProvider.mockTriggerBuild(programmingExercise.getTemplateParticipation());
        String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        doReturn(ObjectId.fromString(dummyHash)).when(gitService).getLastCommitHash(any());

        participationUtilService.addProgrammingParticipationWithResultForExercise(programmingExercise, TEST_PREFIX + "student1");

        ProgrammingExerciseTestCase testCase = testCaseRepository.findByExerciseId(programmingExercise.getId()).iterator().next();

        Set<ProgrammingExerciseTestCaseDTO> programmingExerciseTestCaseDTOS = new HashSet<>();
        ProgrammingExerciseTestCaseDTO programmingExerciseTestCaseDTO = new ProgrammingExerciseTestCaseDTO();
        programmingExerciseTestCaseDTO.setId(testCase.getId());
        programmingExerciseTestCaseDTO.setWeight(400.0);
        programmingExerciseTestCaseDTO.setBonusMultiplier(1.0);
        programmingExerciseTestCaseDTO.setBonusPoints(0.0);
        programmingExerciseTestCaseDTO.setVisibility(Visibility.ALWAYS);
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
    void shouldAllowTestCaseWeightSumZero(AssessmentType assessmentType) throws Exception {
        // for non-automatic exercises the update succeeds and triggers an update
        bambooRequestMockProvider.mockTriggerBuild(programmingExercise.getSolutionParticipation());
        bambooRequestMockProvider.mockTriggerBuild(programmingExercise.getTemplateParticipation());

        programmingExercise.setAssessmentType(assessmentType);
        programmingExerciseRepository.save(programmingExercise);

        var result = ProgrammingExerciseFactory.generateBambooBuildResult("SOLUTION", null, null, null, List.of("test1", "test2", "test3"), Collections.emptyList(), null);
        feedbackCreationService.generateTestCasesFromBuildResult(result, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        Set<ProgrammingExerciseTestCaseDTO> testCaseDTOs = testCases.stream().map(testCase -> {
            final ProgrammingExerciseTestCaseDTO testCaseDTO = new ProgrammingExerciseTestCaseDTO();
            testCaseDTO.setId(testCase.getId());
            testCaseDTO.setBonusMultiplier(testCase.getBonusMultiplier());
            testCaseDTO.setBonusPoints(testCase.getBonusPoints());
            testCaseDTO.setVisibility(testCase.getVisibility());
            testCaseDTO.setWeight(0.0);
            return testCaseDTO;
        }).collect(Collectors.toSet());

        Set<ProgrammingExerciseTestCase> updated = testCaseService.update(programmingExercise.getId(), testCaseDTOs);
        assertThat(updated).hasSize(3).allMatch(testCase -> testCase.getWeight() == 0.0);
    }
}
