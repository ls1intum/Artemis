package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.*;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.service.ProgrammingExerciseService;
import de.tum.in.www1.artemis.service.ProgrammingExerciseTestCaseService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseTestCaseDTO;

public class ProgrammingExerciseTestCaseServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    ParticipationRepository participationRepository;

    @Autowired
    StudentParticipationRepository studentParticipationRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    ProgrammingExerciseTestCaseService testCaseService;

    @Autowired
    ProgrammingExerciseService programmingExerciseService;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    ProgrammingExerciseGradingService gradingService;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    public void setUp() {
        database.addUsers(5, 1, 1);
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        var programmingExercises = programmingExerciseRepository.findAllWithEagerTemplateAndSolutionParticipations();
        programmingExercise = programmingExercises.get(0);
        bambooRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        bambooRequestMockProvider.reset();
    }

    @Test
    public void shouldSetAllTestCasesToInactiveIfFeedbackListIsEmpty() {
        List<Feedback> feedbacks = new ArrayList<>();
        testCaseService.generateTestCasesFromFeedbacks(feedbacks, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).hasSize(3);

        assertThat(testCases.stream().noneMatch(ProgrammingExerciseTestCase::isActive)).isTrue();
    }

    @Test
    public void shouldUpdateActiveFlagsOfTestCases() {
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
    public void shouldGenerateNewTestCases() {
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
    public void shouldNotGenerateNewTestCasesForStaticCodeAnalysisFeedback() {
        testCaseRepository.deleteAll();

        List<Feedback> feedbackList = ModelFactory.generateStaticCodeAnalysisFeedbackList(5);
        testCaseService.generateTestCasesFromFeedbacks(feedbackList, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).hasSize(0);
    }

    @Test
    public void shouldResetTestWeights() throws Exception {
        String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        when(gitService.getLastCommitHash(ArgumentMatchers.any())).thenReturn(ObjectId.fromString(dummyHash));
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
        verify(groupNotificationService, times(1)).notifyInstructorGroupAboutExerciseUpdate(updatedProgrammingExercise, Constants.TEST_CASES_CHANGED_NOTIFICATION);
        verify(websocketMessagingService, times(1)).sendMessage("/topic/programming-exercises/" + programmingExercise.getId() + "/test-cases-changed", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void shouldUpdateTestWeight() throws Exception {
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
        programmingExerciseTestCaseDTOS.add(programmingExerciseTestCaseDTO);

        assertThat(programmingExercise.getTestCasesChanged()).isFalse();

        testCaseService.update(programmingExercise.getId(), programmingExerciseTestCaseDTOS);

        ProgrammingExercise updatedProgrammingExercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).get();

        assertThat(testCaseRepository.findById(testCase.getId()).get().getWeight()).isEqualTo(400);
        assertThat(updatedProgrammingExercise.getTestCasesChanged()).isTrue();
        verify(groupNotificationService, times(1)).notifyInstructorGroupAboutExerciseUpdate(updatedProgrammingExercise, Constants.TEST_CASES_CHANGED_NOTIFICATION);
        verify(websocketMessagingService, times(1)).sendMessage("/topic/programming-exercises/" + programmingExercise.getId() + "/test-cases-changed", true);
    }
}
