package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.connector.IrisRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.LLMTokenUsage;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisLLMCostDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;
import de.tum.cit.aet.artemis.participation.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;

class IrisChatTokenTrackingIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irischattokentrackingintegration";

    @Autowired
    private IrisExerciseChatSessionService irisExerciseChatSessionService;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    @SpyBean
    private LLMTokenUsageService llmTokenUsageService;

    @Autowired
    private IrisRequestMockProvider irisRequestMockProvider;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private PyrisJobService pyrisJobService;

    private ProgrammingExercise exercise;

    private Course course;

    private AtomicBoolean pipelineDone;

    @BeforeEach
    void initTestCase() throws GitAPIException, IOException, URISyntaxException {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        String projectKey = exercise.getProjectKey();
        exercise.setProjectType(ProjectType.PLAIN_GRADLE);
        exercise.setTestRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + projectKey.toLowerCase() + "-tests.git");
        programmingExerciseBuildConfigRepository.save(exercise.getBuildConfig());
        programmingExerciseRepository.save(exercise);
        exercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(exercise.getId()).orElseThrow();

        // Set the correct repository URIs for the template and the solution participation.
        String templateRepositorySlug = projectKey.toLowerCase() + "-exercise";
        TemplateProgrammingExerciseParticipation templateParticipation = exercise.getTemplateParticipation();
        templateParticipation.setRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        String solutionRepositorySlug = projectKey.toLowerCase() + "-solution";
        SolutionProgrammingExerciseParticipation solutionParticipation = exercise.getSolutionParticipation();
        solutionParticipation.setRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + solutionRepositorySlug + ".git");
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

        String assignmentRepositorySlug = projectKey.toLowerCase() + "-" + TEST_PREFIX + "student1";

        // Add a participation for student1.
        ProgrammingExerciseStudentParticipation studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        studentParticipation.setRepositoryUri(String.format(localVCBaseUrl + "/git/%s/%s.git", projectKey, assignmentRepositorySlug));
        studentParticipation.setBranch(defaultBranch);
        programmingExerciseStudentParticipationRepository.save(studentParticipation);

        // Prepare the repositories.
        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, templateRepositorySlug);
        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, projectKey.toLowerCase() + "-tests");
        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, solutionRepositorySlug);
        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, assignmentRepositorySlug);

        // Check that the repository folders were created in the file system for all base repositories.
        localVCLocalCITestService.verifyRepositoryFoldersExist(exercise, localVCBasePath);

        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(exercise);
        pipelineDone = new AtomicBoolean(false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTokenTrackingHandledExerciseChat() throws Exception {
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        var messageToSend = createDefaultMockMessage(irisSession);

        var tokens = getMockLLMCosts();

        List<PyrisStageDTO> doneStage = new ArrayList<>();
        doneStage.add(new PyrisStageDTO("DoneTest", 10, PyrisStageState.DONE, "Done"));

        irisRequestMockProvider.mockRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", doneStage, null, tokens));

            pipelineDone.set(true);
        });

        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.CREATED);

        await().until(pipelineDone::get);

        // Capture the saved token usages
        ArgumentCaptor<List<PyrisLLMCostDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmTokenUsageService).saveIrisTokenUsage(any(PyrisJob.class), any(IrisMessage.class), any(Exercise.class), any(User.class), any(Course.class), captor.capture());

        // Verify that the tokens are saved correctly
        List<PyrisLLMCostDTO> savedTokenUsages = captor.getValue();
        assertEquals(5, savedTokenUsages.size());
        for (int i = 0; i < savedTokenUsages.size(); i++) {
            PyrisLLMCostDTO usage = savedTokenUsages.get(i);
            PyrisLLMCostDTO expectedCost = tokens.get(i);

            assertEquals(expectedCost.numInputTokens(), usage.numInputTokens());
            assertEquals(expectedCost.costPerInputToken(), usage.costPerInputToken());
            assertEquals(expectedCost.numOutputTokens(), usage.numOutputTokens());
            assertEquals(expectedCost.costPerOutputToken(), usage.costPerOutputToken());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTokenTrackingSavedExerciseChat() {

        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        var irisMessage = createDefaultMockMessage(irisSession);
        irisMessageRepository.save(irisMessage);
        String jobToken = pyrisJobService.addExerciseChatJob(course.getId(), exercise.getId(), irisSession.getId());
        PyrisJob job = pyrisJobService.getJob(jobToken);

        var tokens = getMockLLMCosts();

        // Capture the saved token usages
        List<LLMTokenUsage> returnedTokenUsages = llmTokenUsageService.saveIrisTokenUsage(job, irisMessage, exercise, irisSession.getUser(), course, tokens);

        assertEquals(5, returnedTokenUsages.size());
        for (int i = 0; i < returnedTokenUsages.size(); i++) {
            LLMTokenUsage usage = returnedTokenUsages.get(i);
            PyrisLLMCostDTO expectedCost = tokens.get(i);

            assertEquals(expectedCost.modelInfo(), usage.getModel());
            assertEquals(expectedCost.numInputTokens(), usage.getNumInputTokens());
            assertEquals(expectedCost.numOutputTokens(), usage.getNumOutputTokens());
            assertEquals(expectedCost.costPerInputToken(), usage.getCostPerMillionInputTokens());
            assertEquals(expectedCost.costPerOutputToken(), usage.getCostPerMillionOutputTokens());
            assertEquals(expectedCost.pipeline(), usage.getServiceType());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTokenTrackingExerciseChatWithPipelineFail() throws Exception {
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        var messageToSend = createDefaultMockMessage(irisSession);

        var tokens = getMockLLMCosts();

        List<PyrisStageDTO> failedStages = new ArrayList<>();
        failedStages.add(new PyrisStageDTO("TestTokenFail", 10, PyrisStageState.ERROR, "Failed running pipeline"));

        irisRequestMockProvider.mockRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), null, failedStages, null, tokens));

            pipelineDone.set(true);
        });

        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.CREATED);

        await().until(pipelineDone::get);

        // Capture the saved token usages
        ArgumentCaptor<List<PyrisLLMCostDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmTokenUsageService).saveIrisTokenUsage(any(PyrisJob.class), isNull(), any(Exercise.class), any(User.class), any(Course.class), captor.capture());

        // Verify that the tokens are saved correctly
        List<PyrisLLMCostDTO> savedTokenUsages = captor.getValue();
        assertEquals(5, savedTokenUsages.size());
        for (int i = 0; i < savedTokenUsages.size(); i++) {
            PyrisLLMCostDTO usage = savedTokenUsages.get(i);
            PyrisLLMCostDTO expectedCost = tokens.get(i);

            assertEquals(expectedCost.numInputTokens(), usage.numInputTokens());
            assertEquals(expectedCost.costPerInputToken(), usage.costPerInputToken());
            assertEquals(expectedCost.numOutputTokens(), usage.numOutputTokens());
            assertEquals(expectedCost.costPerOutputToken(), usage.costPerOutputToken());
        }
    }

    private List<PyrisLLMCostDTO> getMockLLMCosts() {
        List<PyrisLLMCostDTO> costs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            costs.add(new PyrisLLMCostDTO("test-llm", i * 10 + 5, i * 0.5f, i * 3 + 5, i * 0.12f, LLMServiceType.IRIS_CHAT_EXERCISE_MESSAGE));
        }
        return costs;
    }

    private IrisMessage createDefaultMockMessage(IrisSession irisSession) {
        var messageToSend = irisSession.newMessage();
        messageToSend.addContent(createMockTextContent(), createMockTextContent(), createMockTextContent());
        return messageToSend;
    }

    private IrisMessageContent createMockTextContent() {
        String[] adjectives = { "happy", "sad", "angry", "funny", "silly", "crazy", "beautiful", "smart" };
        String[] nouns = { "dog", "cat", "house", "car", "book", "computer", "phone", "shoe" };

        var rdm = ThreadLocalRandom.current();
        String randomAdjective = adjectives[rdm.nextInt(adjectives.length)];
        String randomNoun = nouns[rdm.nextInt(nouns.length)];

        var text = "The " + randomAdjective + " " + randomNoun + " jumped over the lazy dog.";
        return new IrisTextMessageContent(text);
    }

    private void sendStatus(String jobId, String result, List<PyrisStageDTO> stages, List<String> suggestions, List<PyrisLLMCostDTO> tokens) throws Exception {
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobId))));
        request.postWithoutResponseBody("/api/public/pyris/pipelines/tutor-chat/runs/" + jobId + "/status", new PyrisChatStatusUpdateDTO(result, stages, suggestions, tokens),
                HttpStatus.OK, headers);
    }
}
