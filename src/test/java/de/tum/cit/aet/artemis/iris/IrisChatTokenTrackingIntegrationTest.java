package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.core.connector.IrisRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageRequest;
import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.core.repository.LLMTokenUsageRequestRepository;
import de.tum.cit.aet.artemis.core.repository.LLMTokenUsageTraceRepository;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;
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

    @Autowired
    private LLMTokenUsageService llmTokenUsageService;

    @Autowired
    private LLMTokenUsageTraceRepository irisLLMTokenUsageTraceRepository;

    @Autowired
    private LLMTokenUsageRequestRepository irisLLMTokenUsageRequestRepository;

    @Autowired
    private IrisRequestMockProvider irisRequestMockProvider;

    @Autowired
    private ParticipationUtilService participationUtilService;

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
        // Clean up the database
        irisLLMTokenUsageRequestRepository.deleteAll();
        irisLLMTokenUsageTraceRepository.deleteAll();
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
        irisRequestMockProvider.mockProgrammingExerciseChatResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", doneStage, tokens));
            pipelineDone.set(true);
        });
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.CREATED);
        await().until(pipelineDone::get);
        List<LLMTokenUsageTrace> savedTokenUsageTraces = irisLLMTokenUsageTraceRepository.findAll();
        List<LLMTokenUsageRequest> savedTokenUsageRequests = irisLLMTokenUsageRequestRepository.findAll();
        assertThat(savedTokenUsageTraces).hasSize(1);
        assertThat(savedTokenUsageTraces.getFirst().getServiceType()).isEqualTo(LLMServiceType.IRIS);
        assertThat(savedTokenUsageTraces.getFirst().getExerciseId()).isEqualTo(exercise.getId());
        assertThat(savedTokenUsageTraces.getFirst().getCourseId()).isEqualTo(course.getId());
        assertThat(savedTokenUsageRequests).hasSize(5);
        for (int i = 0; i < savedTokenUsageRequests.size(); i++) {
            LLMTokenUsageRequest usage = savedTokenUsageRequests.get(i);
            LLMRequest expectedCost = tokens.get(i);
            assertThat(usage.getModel()).isEqualTo(expectedCost.model());
            assertThat(usage.getNumInputTokens()).isEqualTo(expectedCost.numInputTokens());
            assertThat(usage.getNumOutputTokens()).isEqualTo(expectedCost.numOutputTokens());
            assertThat(usage.getCostPerMillionInputTokens()).isEqualTo(expectedCost.costPerMillionInputToken());
            assertThat(usage.getCostPerMillionOutputTokens()).isEqualTo(expectedCost.costPerMillionOutputToken());
            assertThat(usage.getServicePipelineId()).isEqualTo(expectedCost.pipelineId());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTokenTrackingSavedExerciseChat() {
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        var irisMessage = createDefaultMockMessage(irisSession);
        irisMessageRepository.save(irisMessage);
        var tokens = getMockLLMCosts();
        LLMTokenUsageTrace tokenUsageTrace = llmTokenUsageService.saveLLMTokenUsage(tokens, LLMServiceType.IRIS,
                builder -> builder.withIrisMessageID(irisMessage.getId()).withExercise(exercise.getId()).withUser(irisSession.getUser().getId()).withCourse(course.getId()));
        assertThat(tokenUsageTrace.getServiceType()).isEqualTo(LLMServiceType.IRIS);
        assertThat(tokenUsageTrace.getIrisMessageId()).isEqualTo(irisMessage.getId());
        assertThat(tokenUsageTrace.getExerciseId()).isEqualTo(exercise.getId());
        assertThat(tokenUsageTrace.getUserId()).isEqualTo(irisSession.getUser().getId());
        assertThat(tokenUsageTrace.getCourseId()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testTokenTrackingExerciseChatWithPipelineFail() throws Exception {
        var irisSession = irisExerciseChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        var messageToSend = createDefaultMockMessage(irisSession);
        var tokens = getMockLLMCosts();
        List<PyrisStageDTO> failedStages = new ArrayList<>();
        failedStages.add(new PyrisStageDTO("TestTokenFail", 10, PyrisStageState.ERROR, "Failed running pipeline"));
        irisRequestMockProvider.mockProgrammingExerciseChatResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), null, failedStages, tokens));
            pipelineDone.set(true);
        });
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.CREATED);
        await().until(pipelineDone::get);
        List<LLMTokenUsageTrace> savedTokenUsageTraces = irisLLMTokenUsageTraceRepository.findAll();
        List<LLMTokenUsageRequest> savedTokenUsageRequests = irisLLMTokenUsageRequestRepository.findAll();
        assertThat(savedTokenUsageTraces).hasSize(1);
        assertThat(savedTokenUsageTraces.getFirst().getServiceType()).isEqualTo(LLMServiceType.IRIS);
        assertThat(savedTokenUsageTraces.getFirst().getExerciseId()).isEqualTo(exercise.getId());
        assertThat(savedTokenUsageTraces.getFirst().getIrisMessageId()).isEqualTo(messageToSend.getId());
        assertThat(savedTokenUsageTraces.getFirst().getCourseId()).isEqualTo(course.getId());
        assertThat(savedTokenUsageRequests).hasSize(5);
        for (int i = 0; i < savedTokenUsageRequests.size(); i++) {
            LLMTokenUsageRequest usage = savedTokenUsageRequests.get(i);
            LLMRequest expectedCost = tokens.get(i);
            assertThat(usage.getModel()).isEqualTo(expectedCost.model());
            assertThat(usage.getNumInputTokens()).isEqualTo(expectedCost.numInputTokens());
            assertThat(usage.getNumOutputTokens()).isEqualTo(expectedCost.numOutputTokens());
            assertThat(usage.getCostPerMillionInputTokens()).isEqualTo(expectedCost.costPerMillionInputToken());
            assertThat(usage.getCostPerMillionOutputTokens()).isEqualTo(expectedCost.costPerMillionOutputToken());
            assertThat(usage.getServicePipelineId()).isEqualTo(expectedCost.pipelineId());
        }
    }

    private List<LLMRequest> getMockLLMCosts() {
        List<LLMRequest> costs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            costs.add(new LLMRequest("test-llm", i * 10 + 5, i * 0.5f, i * 3 + 5, i * 0.12f, "IRIS_CHAT_EXERCISE_MESSAGE"));
        }
        return costs;
    }

    private IrisMessage createDefaultMockMessage(IrisSession irisSession) {
        var messageToSend = irisSession.newMessage();
        messageToSend.addContent(createMockTextContent(), createMockTextContent(), createMockTextContent());
        return messageToSend;
    }

    private IrisMessageContent createMockTextContent() {
        var text = "The happy dog jumped over the lazy dog.";
        return new IrisTextMessageContent(text);
    }

    private void sendStatus(String jobId, String result, List<PyrisStageDTO> stages, List<LLMRequest> tokens) throws Exception {
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobId))));
        request.postWithoutResponseBody("/api/public/pyris/pipelines/tutor-chat/runs/" + jobId + "/status", new PyrisChatStatusUpdateDTO(result, stages, null, tokens),
                HttpStatus.OK, headers);
    }
}
