package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTestCaseType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseTestCaseService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseTestCaseDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

class ProgrammingExerciseTestCaseServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    private ProgrammingExerciseTestCaseService testCaseService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setUp() {
        database.addUsers(5, 1, 0, 1);
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        var programmingExercises = programmingExerciseRepository.findAllWithEagerTemplateAndSolutionParticipations();
        programmingExercise = programmingExercises.get(0);
        bambooRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
        bambooRequestMockProvider.reset();
    }

    @Test
    void shouldSetAllTestCasesToInactiveIfFeedbackListIsEmpty() {
        List<Feedback> feedbacks = new ArrayList<>();
        testCaseService.generateTestCasesFromFeedbacks(feedbacks, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).hasSize(3);

        assertThat(testCases.stream().noneMatch(ProgrammingExerciseTestCase::isActive)).isTrue();
    }

    @Test
    void shouldUpdateActiveFlagsOfTestCases() {
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().text("test1"));
        feedbacks.add(new Feedback().text("test2"));
        feedbacks.add(new Feedback().text("test4"));
        feedbacks.add(new Feedback().text("test5"));
        testCaseService.generateTestCasesFromFeedbacks(feedbacks, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).hasSize(5);

        assertThat(testCases.stream().allMatch(testCase -> {
            if (testCase.getTestName().equals("test3")) {
                return !testCase.isActive();
            }
            else {
                return testCase.isActive();
            }
        })).isTrue();
    }

    @Test
    void shouldGenerateNewTestCases() {
        testCaseRepository.deleteAll();

        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().text("test1"));
        feedbacks.add(new Feedback().text("test2"));
        testCaseService.generateTestCasesFromFeedbacks(feedbacks, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).hasSize(2);

        assertThat(testCases.stream().allMatch(ProgrammingExerciseTestCase::isActive)).isTrue();
    }

    @Test
    void shouldNotGenerateNewTestCasesForStaticCodeAnalysisFeedback() {
        testCaseRepository.deleteAll();

        List<Feedback> feedbackList = ModelFactory.generateStaticCodeAnalysisFeedbackList(5);
        testCaseService.generateTestCasesFromFeedbacks(feedbackList, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).isEmpty();
    }

    @Test
    void shouldFilterOutDuplicateTestCases() {
        testCaseRepository.deleteAll();

        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().text("test1"));
        feedbacks.add(new Feedback().text("generateTestsForAllClasses"));
        feedbacks.add(new Feedback().text("generateTestsForAllClasses"));
        feedbacks.add(new Feedback().text("generateTestsForAllClasses"));
        testCaseService.generateTestCasesFromFeedbacks(feedbacks, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).hasSize(2);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void shouldResetTestWeights() throws Exception {
        String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        when(gitService.getLastCommitHash(any())).thenReturn(ObjectId.fromString(dummyHash));
        database.addProgrammingParticipationWithResultForExercise(programmingExercise, "student1");
        new ArrayList<>(testCaseRepository.findByExerciseId(programmingExercise.getId())).get(0).weight(50.0);
        // After a test case reset, the solution and template repository should be build, so the ContinuousIntegrationService needs to be triggered
        bambooRequestMockProvider.mockTriggerBuild(programmingExercise.getSolutionParticipation());
        bambooRequestMockProvider.mockTriggerBuild(programmingExercise.getTemplateParticipation());

        assertThat(programmingExercise.getTestCasesChanged()).isFalse();

        testCaseService.reset(programmingExercise.getId());

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        ProgrammingExercise updatedProgrammingExercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).get();
        assertThat(testCases.stream().mapToDouble(ProgrammingExerciseTestCase::getWeight).sum()).isEqualTo(testCases.size());
        assertThat(updatedProgrammingExercise.getTestCasesChanged()).isTrue();
        verify(groupNotificationService, times(1)).notifyEditorAndInstructorGroupsAboutChangedTestCasesForProgrammingExercise(updatedProgrammingExercise);
        verify(websocketMessagingService, times(1)).sendMessage("/topic/programming-exercises/" + programmingExercise.getId() + "/test-cases-changed", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void shouldUpdateTestWeight() throws Exception {
        // After a test case update, the solution and template repository should be build, so the ContinuousIntegrationService needs to be triggered
        bambooRequestMockProvider.mockTriggerBuild(programmingExercise.getSolutionParticipation());
        bambooRequestMockProvider.mockTriggerBuild(programmingExercise.getTemplateParticipation());
        String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        doReturn(ObjectId.fromString(dummyHash)).when(gitService).getLastCommitHash(any());

        database.addProgrammingParticipationWithResultForExercise(programmingExercise, "student1");

        ProgrammingExerciseTestCase testCase = testCaseRepository.findAll().get(0);

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
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).get();

        assertThat(testCaseRepository.findById(testCase.getId()).get().getWeight()).isEqualTo(400);
        assertThat(updatedProgrammingExercise.getTestCasesChanged()).isTrue();
        verify(groupNotificationService, times(1)).notifyEditorAndInstructorGroupsAboutChangedTestCasesForProgrammingExercise(updatedProgrammingExercise);
        verify(websocketMessagingService, times(1)).sendMessage("/topic/programming-exercises/" + programmingExercise.getId() + "/test-cases-changed", true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(AssessmentType.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldAllowTestCaseWeightSumZeroManualAssessment(AssessmentType assessmentType) throws Exception {
        // for non-automatic exercises the update succeeds and triggers an update
        if (assessmentType != AssessmentType.AUTOMATIC) {
            bambooRequestMockProvider.mockTriggerBuild(programmingExercise.getSolutionParticipation());
            bambooRequestMockProvider.mockTriggerBuild(programmingExercise.getTemplateParticipation());
        }

        programmingExercise.setAssessmentType(assessmentType);
        programmingExerciseRepository.save(programmingExercise);

        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().text("test1"));
        feedbacks.add(new Feedback().text("test2"));
        feedbacks.add(new Feedback().text("test3"));
        testCaseService.generateTestCasesFromFeedbacks(feedbacks, programmingExercise);

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

        if (assessmentType == AssessmentType.AUTOMATIC) {
            assertThatThrownBy(() -> testCaseService.update(programmingExercise.getId(), testCaseDTOs)).isInstanceOf(BadRequestAlertException.class)
                    .hasMessageContaining("The sum of all test case weights is 0 or below.");
        }
        else {
            Set<ProgrammingExerciseTestCase> updated = testCaseService.update(programmingExercise.getId(), testCaseDTOs);
            assertThat(updated).hasSize(3);
            assertThat(updated).allMatch(testCase -> testCase.getWeight() == 0.0);
        }
    }

    @Test
    void shouldMapStructuralTestCaseTypesCorrectly() {
        Set<ProgrammingExerciseTestCase> structuralTestCases = Set.of(new ProgrammingExerciseTestCase().testName("testClass[Policy]").exercise(programmingExercise),
                new ProgrammingExerciseTestCase().testName("testConstructors[BubbleSort]").exercise(programmingExercise),
                new ProgrammingExerciseTestCase().testName("testMethods[Context]").exercise(programmingExercise),
                new ProgrammingExerciseTestCase().testName("testAttributes[Starter]").exercise(programmingExercise));

        testCaseService.setTestCaseType(structuralTestCases, ProgrammingLanguage.JAVA);
        assertThat(structuralTestCases).allMatch(testCase -> testCase.getType() == ProgrammingExerciseTestCaseType.STRUCTURAL);
    }

    @Test
    void shouldMapBehavioralTestCaseTypesCorrectly() {
        Set<ProgrammingExerciseTestCase> behavioralTestCases = Set.of(new ProgrammingExerciseTestCase().testName("testBubbleSort").exercise(programmingExercise),
                new ProgrammingExerciseTestCase().testName("testMergeSort").exercise(programmingExercise),
                new ProgrammingExerciseTestCase().testName("test13412").exercise(programmingExercise),
                new ProgrammingExerciseTestCase().testName("HiddenRandomTest").exercise(programmingExercise));

        testCaseService.setTestCaseType(behavioralTestCases, ProgrammingLanguage.JAVA);
        assertThat(behavioralTestCases).allMatch(testCase -> testCase.getType() == ProgrammingExerciseTestCaseType.BEHAVIORAL);
    }

    @Test
    void shouldMapNonJavaTestsToDefaultTestCaseType() {
        Set<ProgrammingExerciseTestCase> testCases;

        for (ProgrammingLanguage language : ProgrammingLanguage.values()) {
            if (language == ProgrammingLanguage.JAVA) {
                continue;
            }
            testCases = Set.of(new ProgrammingExerciseTestCase().testName("testBubbleSort").exercise(programmingExercise),
                    new ProgrammingExerciseTestCase().testName("testMergeSort").exercise(programmingExercise),
                    new ProgrammingExerciseTestCase().testName("test13412").exercise(programmingExercise),
                    new ProgrammingExerciseTestCase().testName("HiddenRandomTest").exercise(programmingExercise));
            testCaseService.setTestCaseType(testCases, language);
            assertThat(testCases).allMatch(testCase -> testCase.getType() == ProgrammingExerciseTestCaseType.DEFAULT);
        }
    }
}
