package de.tum.cit.aet.artemis.iris;

import static de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState.DONE;
import static de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState.IN_PROGRESS;
import static de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState.NOT_STARTED;
import static de.tum.cit.aet.artemis.iris.util.IrisChatWebsocketMatchers.messageDTO;
import static de.tum.cit.aet.artemis.iris.util.IrisChatWebsocketMatchers.statusDTO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.dto.MemirisMemoryDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService;
import de.tum.cit.aet.artemis.iris.util.IrisMessageFactory;

class MemirisIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "memirisintegration";

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired
    private IrisCourseChatSessionService irisCourseChatSessionService;

    @Autowired
    private IrisSessionRepository irisSessionRepository;

    @Autowired
    private UserTestRepository userTestRepository;

    private Course course;

    private IrisCourseChatSession irisSession;

    private AtomicBoolean pipelineDone;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();

        activateIrisGlobally();
        activateIrisFor(course);

        // Enable Memiris feature and user flag
        featureToggleService.enableFeature(Feature.Memiris);
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        userTestRepository.updateMemirisEnabled(user.getId(), true);

        irisSession = irisCourseChatSessionService.createSession(course, user, false);

        pipelineDone = new AtomicBoolean(false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void courseChat_AssistantMessageStoresMemories() throws Exception {
        var messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        messageToSend.setMessageDifferentiator(202501);

        irisRequestMockProvider.mockCourseChatResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendCourseStatus(dto.settings().authenticationToken(), "Hello World", dto.initialStages(), List.of("Try this"),
                    List.of(new MemirisMemoryDTO("ACC-1", "Acc Title", "Acc Content", Collections.emptyList(), Collections.emptyList(), false, false)),
                    List.of(new MemirisMemoryDTO("CRT-1", "Crt Title", "Crt Content", Collections.emptyList(), Collections.emptyList(), false, false))));

            pipelineDone.set(true);
        });

        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.CREATED);

        await().until(pipelineDone::get);

        var user = userTestRepository.findByIdElseThrow(irisSession.getUserId());
        verifyWebsocketActivityWasExactly(user.getLogin(), String.valueOf(irisSession.getId()), messageDTO(messageToSend.getContent()), statusDTO(IN_PROGRESS, NOT_STARTED),
                statusDTO(DONE, IN_PROGRESS), messageDTO("Hello World"));

        var sessionFromDb = irisSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId());
        assertThat(sessionFromDb.getMessages()).hasSize(2);
        var assistantMessage = sessionFromDb.getMessages().getLast();
        assertThat(assistantMessage.getSender()).isEqualTo(IrisMessageSender.LLM);
        assertThat(assistantMessage.getAccessedMemories()).isNotNull().hasSize(1);
        assertThat(assistantMessage.getCreatedMemories()).isNotNull().hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void courseChat_IntermediateAccessedMemoriesThenCreatedMemories() throws Exception {
        var messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        messageToSend.setMessageDifferentiator(202502);

        AtomicReference<String> jobIdRef = new AtomicReference<>();
        AtomicReference<List<PyrisStageDTO>> stagesRef = new AtomicReference<>();

        irisRequestMockProvider.mockCourseChatResponse(dto -> {
            jobIdRef.set(dto.settings().authenticationToken());
            stagesRef.set(dto.initialStages());
        });

        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.CREATED);

        await().until(() -> jobIdRef.get() != null && stagesRef.get() != null);

        // Build non-terminal and terminal stage lists
        var preparingDone = stagesRef.get().getFirst();
        var executingInProgress = new PyrisStageDTO("Executing pipeline", 30, de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState.IN_PROGRESS, null);
        var executingDone = new PyrisStageDTO("Executing pipeline", 30, de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState.DONE, null);

        // Send intermediate status with accessed memories only (no result yet) and non-terminal stages
        sendCourseStatus(jobIdRef.get(), null, List.of(preparingDone, executingInProgress), null,
                List.of(new MemirisMemoryDTO("ACC-2", "Acc2", "Acc2 Content", Collections.emptyList(), Collections.emptyList(), false, false)), null);

        // Final status with assistant message and created memories and terminal stages
        sendCourseStatus(jobIdRef.get(), "Hello Again", List.of(preparingDone, executingDone), null, null,
                List.of(new MemirisMemoryDTO("CRT-2", "Crt2", "Crt2 Content", Collections.emptyList(), Collections.emptyList(), false, false)));

        await().until(() -> irisSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId()).getMessages().size() == 2);

        var user = userTestRepository.findByIdElseThrow(irisSession.getUserId());
        // Expect 6 websocket messages in total:
        // 1) user echo, 2) status [IN_PROGRESS, NOT_STARTED], 3) status [DONE, IN_PROGRESS],
        // 4) user message update (accessed memories), 5) status [DONE, IN_PROGRESS], 6) assistant message
        verifyNumberOfCallsToWebsocket(user.getLogin(), String.valueOf(irisSession.getId()), 6);
        // Ensure assistant message was sent
        verifyMessageWasSentOverWebsocket(user.getLogin(), String.valueOf(irisSession.getId()), messageDTO("Hello Again"));

        var sessionFromDb = irisSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId());
        assertThat(sessionFromDb.getMessages()).hasSize(2);
        var userMessage = sessionFromDb.getMessages().getFirst();
        var assistantMessage = sessionFromDb.getMessages().getLast();

        assertThat(userMessage.getSender()).isEqualTo(IrisMessageSender.USER);
        assertThat(userMessage.getAccessedMemories()).isNotNull().hasSize(1);
        assertThat(userMessage.getCreatedMemories()).isNullOrEmpty();

        assertThat(assistantMessage.getSender()).isEqualTo(IrisMessageSender.LLM);
        assertThat(assistantMessage.getCreatedMemories()).isNotNull().hasSize(1);
    }

    private void sendCourseStatus(String jobId, String result, List<PyrisStageDTO> stages, List<String> suggestions, List<MemirisMemoryDTO> accessedMemories,
            List<MemirisMemoryDTO> createdMemories) throws Exception {
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobId))));
        request.postWithoutResponseBody("/api/iris/internal/pipelines/course-chat/runs/" + jobId + "/status",
                new PyrisChatStatusUpdateDTO(result, stages, null, suggestions, null, accessedMemories, createdMemories), HttpStatus.OK, headers);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void courseChat_CreatedMemoriesUpdateAssistantMessage() throws Exception {
        var messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        messageToSend.setMessageDifferentiator(202503);

        AtomicReference<String> jobIdRef = new AtomicReference<>();
        AtomicReference<List<PyrisStageDTO>> stagesRef = new AtomicReference<>();

        irisRequestMockProvider.mockCourseChatResponse(dto -> {
            jobIdRef.set(dto.settings().authenticationToken());
            stagesRef.set(dto.initialStages());
        });

        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.CREATED);

        await().until(() -> jobIdRef.get() != null && stagesRef.get() != null);

        var preparingDone = stagesRef.get().getFirst();
        var executingInProgress = new PyrisStageDTO("Executing pipeline", 30, de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState.IN_PROGRESS, null);

        // First: send assistant result to create assistant message and set assistantMessageId on the job (keep job running with non-terminal stages)
        sendCourseStatus(jobIdRef.get(), "Initial Answer", List.of(preparingDone, executingInProgress), null, null, null);

        // Then: send only created memories (no result), which should update the existing assistant message and resend it via websocket
        sendCourseStatus(jobIdRef.get(), null, List.of(preparingDone, executingInProgress), null, null,
                List.of(new MemirisMemoryDTO("CRT-3", "Crt3", "Crt3 Content", Collections.emptyList(), Collections.emptyList(), false, false)));

        await().until(() -> irisSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId()).getMessages().size() == 2);

        var sessionFromDb = irisSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId());
        assertThat(sessionFromDb.getMessages()).hasSize(2);
        var assistantMessage = sessionFromDb.getMessages().getLast();
        assertThat(assistantMessage.getSender()).isEqualTo(IrisMessageSender.LLM);
        assertThat(assistantMessage.getContent().getFirst().getContentAsString()).isEqualTo("Initial Answer");
        assertThat(assistantMessage.getCreatedMemories()).isNotNull().hasSize(1);
    }
}
