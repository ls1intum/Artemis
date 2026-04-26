package de.tum.cit.aet.artemis.iris;

import static de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState.DONE;
import static de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState.IN_PROGRESS;
import static de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState.NOT_STARTED;
import static de.tum.cit.aet.artemis.iris.util.IrisChatWebsocketMatchers.messageDTO;
import static de.tum.cit.aet.artemis.iris.util.IrisChatWebsocketMatchers.statusDTO;
import static de.tum.cit.aet.artemis.iris.util.IrisChatWebsocketMatchers.suggestionsDTO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisMcqResponseDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageContentDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageRequestDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageResponseDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisSessionService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.util.IrisChatSessionFactory;
import de.tum.cit.aet.artemis.iris.util.IrisMessageFactory;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;

/**
 * Integration tests for the Iris chat <em>message</em> lifecycle across all chat modes: posting a
 * user message → Pyris pipeline execution → websocket frames, message history retrieval, helpful
 * rating, resend, rate limiting, session title updates, and mode-specific behaviours (programming
 * uncommitted-files, tutor suggestions, MCQ content classification).
 *
 * <p>
 * Session-lifecycle tests (create / getCurrent / delete / overview) live in
 * {@link IrisChatSessionResourceTest}; this class covers only message-level behaviour.
 */
class IrisChatMessageIntegrationTest extends AbstractIrisChatSessionTest {

    private static final String TEST_PREFIX = "irismessageintegration";

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private IrisSessionRepository irisSessionRepository;

    @Autowired
    private IrisChatSessionRepository irisChatSessionRepository;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    @Autowired
    private IrisSessionService irisSessionService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private AtomicBoolean pipelineDone;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeEach
    void initMessageTestState() {
        pipelineDone = new AtomicBoolean(false);
    }

    // =========================================================================
    // Mode-uniform message flow (parametrized across the four chat modes)
    // =========================================================================

    @ParameterizedTest
    @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendOneMessage_forwardsPyrisResponseViaWebsocket(IrisChatMode mode) throws Exception {
        IrisChatSession session = createSessionForUser(mode, "student1");
        IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(session);
        messageToSend.setMessageDifferentiator(1453);

        mockChatResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", dto.initialStages(), null, null));
            pipelineDone.set(true);
        });

        request.postWithoutResponseBody(messagesUrl(session), messageToSend, HttpStatus.CREATED);
        await().until(pipelineDone::get);

        User user = userTestRepository.findByIdElseThrow(session.getUserId());
        verifyWebsocketActivityWasExactly(user.getLogin(), String.valueOf(session.getId()), messageDTO(messageToSend.getContent()), statusDTO(IN_PROGRESS, NOT_STARTED),
                statusDTO(DONE, IN_PROGRESS), messageDTO("Hello World"));
    }

    @ParameterizedTest
    @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendOneMessage_forwardsCustomInstructionsToPyris(IrisChatMode mode) throws Exception {
        String customInstructions = "Please focus on clarity.";
        var courseSettings = irisSettingsService.getSettingsForCourse(course);
        configureCourseSettings(course, customInstructions, courseSettings.variant());

        IrisChatSession session = createSessionForUser(mode, "student1");
        IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(session);

        mockChatResponse(dto -> {
            assertThat(dto.customInstructions()).isEqualTo(customInstructions);
            pipelineDone.set(true);
        });

        request.postWithoutResponseBody(messagesUrl(session), messageToSend, HttpStatus.CREATED);
        await().until(pipelineDone::get);
    }

    @ParameterizedTest
    @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendTwoMessages_persistsBothUserAndLlmMessages(IrisChatMode mode) throws Exception {
        IrisChatSession session = createSessionForUser(mode, "student1");

        mockChatResponse(dto -> assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World 1", dto.initialStages(), null, null)));
        mockChatResponse(dto -> assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World 2", dto.initialStages(), null, null)));

        request.postWithoutResponseBody(messagesUrl(session), IrisMessageFactory.createIrisMessageForSessionWithContent(session), HttpStatus.CREATED);
        request.postWithoutResponseBody(messagesUrl(session), IrisMessageFactory.createIrisMessageForSessionWithContent(session), HttpStatus.CREATED);

        verify(websocketMessagingService, times(8)).sendMessageToUser(eq(TEST_PREFIX + "student1"), eq("/topic/iris/" + session.getId()), any());
        assertThat(irisSessionRepository.findByIdWithMessagesElseThrow(session.getId()).getMessages()).hasSize(4);
    }

    @ParameterizedTest
    @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void sendMessage_returns403WhenNotSessionOwner(IrisChatMode mode) throws Exception {
        IrisChatSession otherUsersSession = createSessionForUser(mode, "student1");
        IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(otherUsersSession);
        request.postWithoutResponseBody(messagesUrl(otherUsersSession), messageToSend, HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendMessage_returns400WhenContentEmpty(IrisChatMode mode) throws Exception {
        IrisChatSession session = createSessionForUser(mode, "student1");
        IrisMessage messageWithoutContent = IrisMessageFactory.createIrisMessageForSession(session);
        request.postWithoutResponseBody(messagesUrl(session), messageWithoutContent, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getMessages_returnsAllMessagesForSession(IrisChatMode mode) throws Exception {
        IrisChatSession session = createSessionForUser(mode, "student1");
        IrisMessage m1 = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(session), session, IrisMessageSender.USER);
        IrisMessage m2 = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(session), session, IrisMessageSender.LLM);
        IrisMessage m3 = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(session), session, IrisMessageSender.USER);
        IrisMessage m4 = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(session), session, IrisMessageSender.LLM);

        var messages = request.getList(messagesUrl(session), HttpStatus.OK, IrisMessageResponseDTO.class);

        assertThat(messages).extracting(IrisMessageResponseDTO::id).containsExactlyInAnyOrder(m1.getId(), m2.getId(), m3.getId(), m4.getId());
    }

    @ParameterizedTest
    @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessage_persistsHelpfulTrue(IrisChatMode mode) throws Exception {
        assertHelpfulRatingPersisted(mode, true);
    }

    @ParameterizedTest
    @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessage_persistsHelpfulFalse(IrisChatMode mode) throws Exception {
        assertHelpfulRatingPersisted(mode, false);
    }

    @ParameterizedTest
    @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessage_persistsHelpfulNull(IrisChatMode mode) throws Exception {
        assertHelpfulRatingPersisted(mode, null);
    }

    @ParameterizedTest
    @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessage_returns400WhenMessageSenderIsUser(IrisChatMode mode) throws Exception {
        IrisChatSession session = createSessionForUser(mode, "student1");
        IrisMessage userMessage = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(session), session, IrisMessageSender.USER);
        request.putWithResponseBody(helpfulUrl(session, userMessage), true, IrisMessageResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessage_returns409WhenMessageBelongsToDifferentSession(IrisChatMode mode) throws Exception {
        IrisChatSession session1 = createSessionForUser(mode, "student1");
        IrisChatSession session2 = createSessionForUser(mode, "student2");
        IrisMessage message = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(session1), session1, IrisMessageSender.USER);
        request.putWithResponseBody(helpfulUrl(session2, message), true, IrisMessageResponseDTO.class, HttpStatus.CONFLICT);
    }

    @ParameterizedTest
    @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void resendMessage_reprocessesExistingUserMessage(IrisChatMode mode) throws Exception {
        IrisChatSession session = createSessionForUser(mode, "student1");
        IrisMessage userMessage = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(session), session, IrisMessageSender.USER);

        mockChatResponse(dto -> {
            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", dto.initialStages(), null, null));
            pipelineDone.set(true);
        });

        request.postWithoutResponseBody(messagesUrl(session) + "/" + userMessage.getId() + "/resend", null, HttpStatus.OK);
        await().until(() -> irisSessionRepository.findByIdWithMessagesElseThrow(session.getId()).getMessages().size() == 2);

        User user = userTestRepository.findByIdElseThrow(session.getUserId());
        verifyWebsocketActivityWasExactly(user.getLogin(), String.valueOf(session.getId()), statusDTO(IN_PROGRESS, NOT_STARTED), statusDTO(DONE, IN_PROGRESS),
                messageDTO("Hello World"));
    }

    @ParameterizedTest
    @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendMessage_updatesSessionTitleWhenPyrisReturnsOne(IrisChatMode mode) throws Exception {
        IrisChatSession session = createSessionForUser(mode, "student1");
        IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(session);
        final String expectedTitle = "New chat";

        mockChatResponse(dto -> {
            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", dto.initialStages(), expectedTitle, null));
            pipelineDone.set(true);
        });

        request.postWithoutResponseBody(messagesUrl(session), messageToSend, HttpStatus.CREATED);
        await().until(pipelineDone::get);

        assertThat(irisSessionRepository.findByIdWithMessagesElseThrow(session.getId()).getTitle()).isEqualTo(expectedTitle);
    }

    // Each mode uses a different session owner so that LLM-message counts from earlier parametrized
    // invocations don't trip the rate limit on the first POST of the next invocation.
    @ParameterizedTest
    @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
    void sendMessage_returns429WhenRateLimitReached(IrisChatMode mode) throws Exception {
        String ownerLogin = switch (mode) {
            case COURSE_CHAT -> "student1";
            case LECTURE_CHAT -> "student2";
            case TEXT_EXERCISE_CHAT -> "student3";
            case PROGRAMMING_EXERCISE_CHAT -> "tutor1";
            default -> throw new IllegalArgumentException(mode + " is not a chat-session mode");
        };
        userUtilService.changeUser(TEST_PREFIX + ownerLogin);

        // Purge prior sessions (and their LLM messages) so this user's rate-limit count starts at 0.
        long ownerId = userUtilService.getUserByLogin(TEST_PREFIX + ownerLogin).getId();
        irisChatSessionRepository.deleteAll(irisChatSessionRepository.findAll().stream().filter(s -> s.getUserId() == ownerId).toList());

        IrisChatSession session = createSessionForUser(mode, ownerLogin);
        IrisMessage first = IrisMessageFactory.createIrisMessageForSessionWithContent(session);
        IrisMessage second = IrisMessageFactory.createIrisMessageForSessionWithContent(session);

        mockChatResponse(dto -> {
            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", dto.initialStages(), null, null));
            pipelineDone.set(true);
        });

        configureCourseRateLimit(course, 1, 10);
        try {
            request.postWithoutResponseBody(messagesUrl(session), first, HttpStatus.CREATED);
            await().until(() -> irisSessionRepository.findByIdWithMessagesElseThrow(session.getId()).getMessages().size() == 2);
            request.postWithoutResponseBody(messagesUrl(session), second, HttpStatus.TOO_MANY_REQUESTS);
            IrisMessage saved = irisMessageService.saveMessage(second, session, IrisMessageSender.USER);
            request.postWithoutResponseBody(messagesUrl(session) + "/" + saved.getId() + "/resend", null, HttpStatus.TOO_MANY_REQUESTS);

            User user = userTestRepository.findByIdElseThrow(session.getUserId());
            verifyWebsocketActivityWasExactly(user.getLogin(), String.valueOf(session.getId()), messageDTO(first.getContent()), statusDTO(IN_PROGRESS, NOT_STARTED),
                    statusDTO(DONE, IN_PROGRESS), messageDTO("Hello World"));
        }
        finally {
            configureCourseRateLimit(course, null, null);
        }
    }

    // =========================================================================
    // Entity-deletion cascade: removing the owning exercise or lecture must also remove any
    // Iris chat sessions (and their messages) referencing it. The unified iris_session schema
    // stores entity_id as a plain long with no FK, so cleanup happens explicitly in the
    // exercise/lecture deletion services via IrisChatSessionApi.
    // =========================================================================

    @ParameterizedTest
    @EnumSource(value = IrisChatMode.class, names = { "TEXT_EXERCISE_CHAT", "LECTURE_CHAT" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteOwningEntity_cascadesSessionsAndMessages(IrisChatMode mode) throws Exception {
        IrisChatSession session = createSessionForUser(mode, "instructor1");
        irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(session), session, IrisMessageSender.USER);
        irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(session), session, IrisMessageSender.LLM);

        String url = switch (mode) {
            case TEXT_EXERCISE_CHAT -> "/api/text/text-exercises/" + textExercise.getId();
            case LECTURE_CHAT -> "/api/lecture/lectures/" + lecture.getId();
            default -> throw new IllegalArgumentException("Unsupported mode: " + mode);
        };
        request.delete(url, HttpStatus.OK);

        assertThat(irisChatSessionRepository.findById(session.getId())).isEmpty();
        assertThat(irisMessageRepository.findAllBySessionId(session.getId())).isEmpty();
    }

    // =========================================================================
    // Citation service — only invoked for lecture and text exercise chats
    // =========================================================================

    @Nested
    class CitationService {

        @ParameterizedTest
        @EnumSource(value = IrisChatMode.class, names = { "LECTURE_CHAT", "TEXT_EXERCISE_CHAT" })
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void sendOneMessage_invokesCitationService(IrisChatMode mode) throws Exception {
            IrisChatSession session = createSessionForUser(mode, "student1");
            IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(session);

            mockChatResponse(dto -> {
                assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", dto.initialStages(), null, null));
                pipelineDone.set(true);
            });

            request.postWithoutResponseBody(messagesUrl(session), messageToSend, HttpStatus.CREATED);
            await().until(pipelineDone::get);

            verify(irisCitationService).resolveCitationInfo(any());
        }
    }

    // =========================================================================
    // Programming-exercise-specific behaviour: team exercises, uncommitted files,
    // repository-aware message DTOs, deletion cascade.
    // =========================================================================

    @Nested
    class ProgrammingExerciseChat {

        private ProgrammingExercise teamExercise;

        private ProgrammingExerciseStudentParticipation soloParticipation;

        private ProgrammingExerciseStudentParticipation teamParticipation;

        @BeforeEach
        void setupProgrammingRepositories() throws GitAPIException, IOException, URISyntaxException {
            // The base fixture's programming exercise is a bare bean without template/solution
            // participations. Replace it with a fully-provisioned exercise so the chat pipeline can
            // be exercised end-to-end (repository URIs, submissions, LocalVC repos).
            programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, false, ProgrammingLanguage.JAVA, "ProgrammingSolo", "SOLOEXC", null);
            activateIrisFor(programmingExercise);
            soloParticipation = provisionProgrammingRepositories(programmingExercise);

            teamExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, false, ProgrammingLanguage.JAVA, "ProgrammingTeam", "TEAMEXC", null);
            teamExercise.setMode(ExerciseMode.TEAM);
            programmingExerciseRepository.save(teamExercise);

            Team team = new Team();
            team.setName("Team 1");
            team.setShortName("team1");
            team.setExercise(teamExercise);
            team.setStudents(Set.of(userUtilService.getUserByLogin(TEST_PREFIX + "student2"), userUtilService.getUserByLogin(TEST_PREFIX + "student3")));
            team.setOwner(userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
            Team savedTeam = teamRepository.save(team);

            teamParticipation = provisionProgrammingRepositoriesForTeam(teamExercise, savedTeam);
            activateIrisFor(teamExercise);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
        void sendOneMessage_worksForTeamExercise() throws Exception {
            long submissionId = teamParticipation.getSubmissions().iterator().next().getId();
            IrisChatSession session = createProgrammingSession(teamExercise, "student2");
            IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(session);

            irisRequestMockProvider.mockProgrammingExerciseChatResponseExpectingSubmissionId(dto -> {
                assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", dto.initialStages(), null, null));
                pipelineDone.set(true);
            }, submissionId);

            request.postWithoutResponseBody(messagesUrl(session), messageToSend, HttpStatus.CREATED);
            await().until(pipelineDone::get);

            User user = userTestRepository.findByIdElseThrow(session.getUserId());
            verifyWebsocketActivityWasExactly(user.getLogin(), String.valueOf(session.getId()), messageDTO(messageToSend.getContent()), statusDTO(IN_PROGRESS, NOT_STARTED),
                    statusDTO(DONE, IN_PROGRESS), messageDTO("Hello World"));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void sendOneMessage_forwardsSubmissionIdToPyris() throws Exception {
            long submissionId = soloParticipation.getSubmissions().iterator().next().getId();
            IrisChatSession session = createProgrammingSession(programmingExercise, "student1");
            IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(session);

            irisRequestMockProvider.mockProgrammingExerciseChatResponseExpectingSubmissionId(_ -> pipelineDone.set(true), submissionId);

            request.postWithoutResponseBody(messagesUrl(session), messageToSend, HttpStatus.CREATED);
            await().until(pipelineDone::get);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void createMessage_acceptsUncommittedFilesInRequestDto() throws Exception {
            IrisChatSession session = createProgrammingSession(programmingExercise, "student1");
            Map<String, String> uncommittedFiles = Map.of("src/Main.java", "public class Main { /* uncommitted */ }", "src/Utils.java", "public class Utils { }");

            mockChatResponse(dto -> {
                if (dto.programmingExerciseSubmission() != null && dto.programmingExerciseSubmission().repository() != null) {
                    assertThat(dto.programmingExerciseSubmission().repository()).containsAllEntriesOf(uncommittedFiles);
                }
            });

            IrisMessageRequestDTO requestDto = buildTextRequestDto(session, uncommittedFiles);
            var response = request.postWithResponseBody(messagesUrl(session), requestDto, IrisMessageResponseDTO.class, HttpStatus.CREATED);
            assertThat(response.id()).isNotNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void createMessage_worksWithoutUncommittedFiles() throws Exception {
            IrisChatSession session = createProgrammingSession(programmingExercise, "student1");
            mockChatResponse(_ -> {
            });

            IrisMessageRequestDTO requestDto = buildTextRequestDto(session, Map.of());
            var response = request.postWithResponseBody(messagesUrl(session), requestDto, IrisMessageResponseDTO.class, HttpStatus.CREATED);
            assertThat(response.id()).isNotNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void createMessage_preservesJsonContentPayload() throws Exception {
            IrisChatSession session = createProgrammingSession(programmingExercise, "student1");
            mockChatResponse(_ -> {
            });

            IrisMessage source = new IrisMessage();
            source.addContent(new IrisJsonMessageContent(JsonNodeFactory.instance.objectNode().put("k1", "v1").put("k2", "v2").put("k3", "v3")));
            List<IrisMessageContentDTO> contentDtos = source.getContent().stream().map(content -> new IrisMessageContentDTO("json", null, content.getContentAsString())).toList();
            IrisMessageRequestDTO requestDto = new IrisMessageRequestDTO(contentDtos, 42, Map.of());

            var response = request.postWithResponseBody(messagesUrl(session), requestDto, IrisMessageResponseDTO.class, HttpStatus.CREATED);

            assertThat(response.content()).hasSize(1);
            assertThat(response.content().getFirst().type()).isEqualTo("json");
            assertThat(response.content().getFirst().attributes()).isNotNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void requestMessageFromIris_passesUncommittedFilesThroughToPyrisDto() {
            IrisChatSession session = createProgrammingSession(programmingExercise, "student1");
            irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(session), session, IrisMessageSender.USER);

            Map<String, String> uncommittedFiles = Map.of("src/Main.java", "public class Main { }");
            mockChatResponse(dto -> {
                if (dto.programmingExerciseSubmission() != null && dto.programmingExerciseSubmission().repository() != null) {
                    assertThat(dto.programmingExerciseSubmission().repository()).containsAllEntriesOf(uncommittedFiles);
                }
            });

            irisSessionService.requestMessageFromIris(session, uncommittedFiles);

            assertThat(irisMessageRepository.findAllBySessionId(session.getId()).stream().anyMatch(m -> m.getSender() == IrisMessageSender.USER)).isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void requestMessageFromIris_backwardCompatibleOverloadWorks() {
            IrisChatSession session = createProgrammingSession(programmingExercise, "student1");
            irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(session), session, IrisMessageSender.USER);
            mockChatResponse(_ -> {
            });

            irisSessionService.requestMessageFromIris(session);

            assertThat(irisMessageRepository.findAllBySessionId(session.getId()).stream().anyMatch(m -> m.getSender() == IrisMessageSender.USER)).isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void deleteProgrammingExercise_cascadesSessionsAndMessages() throws Exception {
            IrisChatSession session = createProgrammingSession(programmingExercise, "instructor1");
            irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(session), session, IrisMessageSender.USER);
            irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(session), session, IrisMessageSender.LLM);

            String url = "/api/programming/programming-exercises/" + programmingExercise.getId() + "?deleteStudentReposBuildPlans=false&deleteBaseReposBuildPlans=false";
            request.delete(url, HttpStatus.OK);

            assertThat(irisChatSessionRepository.findById(session.getId())).isEmpty();
            assertThat(irisMessageRepository.findAllBySessionId(session.getId())).isEmpty();
        }

        private IrisMessageRequestDTO buildTextRequestDto(IrisChatSession session, Map<String, String> uncommittedFiles) {
            IrisMessage source = IrisMessageFactory.createIrisMessageForSessionWithContent(session);
            List<IrisMessageContentDTO> contentDtos = source.getContent().stream().map(content -> new IrisMessageContentDTO("text", content.getContentAsString(), null)).toList();
            return new IrisMessageRequestDTO(contentDtos, source.getMessageDifferentiator(), uncommittedFiles);
        }

        private ProgrammingExerciseStudentParticipation provisionProgrammingRepositories(ProgrammingExercise exercise) throws GitAPIException, IOException, URISyntaxException {
            String projectKey = exercise.getProjectKey();
            exercise.setProjectType(ProjectType.PLAIN_GRADLE);
            exercise.setTestRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + projectKey.toLowerCase() + "-tests.git");
            programmingExerciseBuildConfigRepository.save(exercise.getBuildConfig());
            programmingExerciseRepository.save(exercise);
            ProgrammingExercise reloaded = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(exercise.getId()).orElseThrow();

            String templateSlug = projectKey.toLowerCase() + "-exercise";
            TemplateProgrammingExerciseParticipation templateParticipation = reloaded.getTemplateParticipation();
            templateParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + templateSlug + ".git");
            templateProgrammingExerciseParticipationRepository.save(templateParticipation);

            String solutionSlug = projectKey.toLowerCase() + "-solution";
            SolutionProgrammingExerciseParticipation solutionParticipation = reloaded.getSolutionParticipation();
            solutionParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + solutionSlug + ".git");
            solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

            String assignmentSlug = projectKey.toLowerCase() + "-" + TEST_PREFIX + "student1";
            ProgrammingExerciseStudentParticipation studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(reloaded,
                    TEST_PREFIX + "student1");
            participationUtilService.addSubmission(studentParticipation, ParticipationFactory.generateProgrammingSubmission(true));
            studentParticipation.setRepositoryUri(String.format(localVCBaseUri + "/git/%s/%s.git", projectKey, assignmentSlug));
            studentParticipation.setBranch(defaultBranch);
            programmingExerciseStudentParticipationRepository.save(studentParticipation);

            localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, templateSlug);
            localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, projectKey.toLowerCase() + "-tests");
            localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, solutionSlug);
            localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, assignmentSlug);
            localVCLocalCITestService.verifyRepositoryFoldersExist(reloaded, localVCBasePath);

            return studentParticipation;
        }

        private ProgrammingExerciseStudentParticipation provisionProgrammingRepositoriesForTeam(ProgrammingExercise exercise, Team team)
                throws GitAPIException, IOException, URISyntaxException {
            String projectKey = exercise.getProjectKey();
            exercise.setProjectType(ProjectType.PLAIN_GRADLE);
            exercise.setTestRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + projectKey.toLowerCase() + "-tests.git");
            programmingExerciseBuildConfigRepository.save(exercise.getBuildConfig());
            programmingExerciseRepository.save(exercise);
            ProgrammingExercise reloaded = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(exercise.getId()).orElseThrow();

            String templateSlug = projectKey.toLowerCase() + "-exercise";
            TemplateProgrammingExerciseParticipation templateParticipation = reloaded.getTemplateParticipation();
            templateParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + templateSlug + ".git");
            templateProgrammingExerciseParticipationRepository.save(templateParticipation);

            String solutionSlug = projectKey.toLowerCase() + "-solution";
            SolutionProgrammingExerciseParticipation solutionParticipation = reloaded.getSolutionParticipation();
            solutionParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + solutionSlug + ".git");
            solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

            String assignmentSlug = projectKey.toLowerCase() + "-" + TEST_PREFIX + "team1";
            ProgrammingExerciseStudentParticipation studentParticipation = participationUtilService.addTeamParticipationForProgrammingExercise(reloaded, team);
            participationUtilService.addSubmission(studentParticipation, ParticipationFactory.generateProgrammingSubmission(true));
            studentParticipation.setRepositoryUri(String.format(localVCBaseUri + "/git/%s/%s.git", projectKey, assignmentSlug));
            studentParticipation.setBranch(defaultBranch);
            programmingExerciseStudentParticipationRepository.save(studentParticipation);

            localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, templateSlug);
            localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, projectKey.toLowerCase() + "-tests");
            localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, solutionSlug);
            localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, assignmentSlug);
            localVCLocalCITestService.verifyRepositoryFoldersExist(reloaded, localVCBasePath);

            return studentParticipation;
        }

        private IrisChatSession createProgrammingSession(ProgrammingExercise exercise, String studentLogin) {
            User user = userUtilService.getUserByLogin(TEST_PREFIX + studentLogin);
            return irisChatSessionRepository.save(IrisChatSessionFactory.createProgrammingExerciseChatSessionForUser(exercise, user));
        }
    }

    // =========================================================================
    // Tutor suggestions: Pyris returns suggestion strings that are forwarded as
    // a suggestionsDTO websocket frame rather than as a message reply.
    // =========================================================================

    @Nested
    class TutorSuggestions {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void pyrisSuggestionsAreForwardedOverWebsocket() throws Exception {
            IrisChatSession session = createSessionForUser(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, "student1");
            IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(session);

            mockChatResponse(dto -> {
                List<String> suggestions = List.of("suggestion1", "suggestion2", "suggestion3");
                assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), null, dto.initialStages(), null, suggestions));
                pipelineDone.set(true);
            });

            request.postWithoutResponseBody(messagesUrl(session), messageToSend, HttpStatus.CREATED);
            await().until(pipelineDone::get);

            User user = userTestRepository.findByIdElseThrow(session.getUserId());
            verifyWebsocketActivityWasExactly(user.getLogin(), String.valueOf(session.getId()), messageDTO(messageToSend.getContent()), statusDTO(IN_PROGRESS, NOT_STARTED),
                    statusDTO(DONE, IN_PROGRESS), suggestionsDTO("suggestion1", "suggestion2", "suggestion3"));
        }
    }

    // =========================================================================
    // MCQ / JSON content classification: Pyris may return plain text, MCQ JSON,
    // mixed text+JSON, or malformed MCQ; the server must classify and persist
    // each case correctly. These tests exercise the classification layer
    // through the normal programming-exercise message pipeline.
    // =========================================================================

    @Nested
    class McqAndJsonContent {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void mcqJsonIsStoredAsJsonContent() throws Exception {
            String mcqJson = """
                    {"type":"mcq","question":"What is 2+2?","options":[{"text":"3","correct":false},{"text":"4","correct":true}],"explanation":"Basic arithmetic."}""";

            var content = sendAndGetLlmResponseContent(mcqJson).getFirst();

            assertThat(content).isInstanceOf(IrisJsonMessageContent.class);
            assertThat(((IrisJsonMessageContent) content).getJsonNode().get("type").asText()).isEqualTo("mcq");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void plainTextIsStoredAsTextContent() throws Exception {
            var content = sendAndGetLlmResponseContent("Just a text response").getFirst();
            assertThat(content).isInstanceOf(IrisTextMessageContent.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void nonMcqJsonIsStoredAsTextContent() throws Exception {
            var content = sendAndGetLlmResponseContent("""
                    {"type":"other","data":"something"}""").getFirst();
            assertThat(content).isInstanceOf(IrisTextMessageContent.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void invalidJsonIsStoredAsTextContent() throws Exception {
            var content = sendAndGetLlmResponseContent("{this is not valid json}").getFirst();
            assertThat(content).isInstanceOf(IrisTextMessageContent.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void mixedTextAndMcqJsonIsSplitIntoMultipleContents() throws Exception {
            String mixed = "Here are your questions! "
                    + "{\"type\":\"mcq\",\"question\":\"What is X?\",\"options\":[{\"text\":\"A\",\"correct\":false},{\"text\":\"B\",\"correct\":true},{\"text\":\"C\",\"correct\":false},{\"text\":\"D\",\"correct\":false}],\"explanation\":\"B is correct.\"}"
                    + " Good luck!";

            var contents = sendAndGetLlmResponseContent(mixed);

            assertThat(contents).hasSize(3);
            assertThat(((IrisTextMessageContent) contents.get(0)).getTextContent()).isEqualTo("Here are your questions!");
            assertThat(((IrisJsonMessageContent) contents.get(1)).getJsonNode().get("type").asText()).isEqualTo("mcq");
            assertThat(((IrisTextMessageContent) contents.get(2)).getTextContent()).isEqualTo("Good luck!");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void mixedTextAndMcqSetJsonIsSplitIntoMultipleContents() throws Exception {
            String mixed = "Here are your questions! "
                    + "{\"type\":\"mcq-set\",\"questions\":[{\"question\":\"What is X?\",\"options\":[{\"text\":\"A\",\"correct\":false},{\"text\":\"B\",\"correct\":true},{\"text\":\"C\",\"correct\":false},{\"text\":\"D\",\"correct\":false}],\"explanation\":\"B is correct.\"}]}"
                    + " Good luck!";

            var contents = sendAndGetLlmResponseContent(mixed);

            assertThat(contents).hasSize(3);
            assertThat(((IrisJsonMessageContent) contents.get(1)).getJsonNode().get("type").asText()).isEqualTo("mcq-set");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void textWithNonMcqJsonIsStoredAsSingleTextContent() throws Exception {
            String input = "The config is {\"key\": \"value\"}";
            var contents = sendAndGetLlmResponseContent(input);

            assertThat(contents).hasSize(1);
            assertThat(((IrisTextMessageContent) contents.getFirst()).getTextContent()).isEqualTo(input);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void pureMcqJsonIsStoredAsSingleJsonContent() throws Exception {
            String mcqJson = "{\"type\":\"mcq\",\"question\":\"What is 2+2?\",\"options\":[{\"text\":\"3\",\"correct\":false},{\"text\":\"4\",\"correct\":true}],\"explanation\":\"Basic arithmetic.\"}";
            var contents = sendAndGetLlmResponseContent(mcqJson);

            assertThat(contents).hasSize(1);
            var jsonContent = (IrisJsonMessageContent) contents.getFirst();
            assertThat(jsonContent.getJsonNode().get("type").asText()).isEqualTo("mcq");
            assertThat(jsonContent.getJsonNode().get("question").asText()).isEqualTo("What is 2+2?");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void malformedMcq_zeroCorrect_showsFallbackMessage() throws Exception {
            assertMalformedMcqShowsFallback("""
                    {"type":"mcq","question":"What is 2+2?","options":[{"text":"3","correct":false},{"text":"5","correct":false}],"explanation":"Basic arithmetic."}""");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void malformedMcq_twoCorrect_showsFallbackMessage() throws Exception {
            assertMalformedMcqShowsFallback("""
                    {"type":"mcq","question":"What is 2+2?","options":[{"text":"4","correct":true},{"text":"four","correct":true}],"explanation":"Basic arithmetic."}""");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void malformedMcq_onlyOneOption_showsFallbackMessage() throws Exception {
            assertMalformedMcqShowsFallback("""
                    {"type":"mcq","question":"What is 2+2?","options":[{"text":"4","correct":true}],"explanation":"Basic arithmetic."}""");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void malformedMcq_missingExplanation_showsFallbackMessage() throws Exception {
            assertMalformedMcqShowsFallback("""
                    {"type":"mcq","question":"What is 2+2?","options":[{"text":"3","correct":false},{"text":"4","correct":true}]}""");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void malformedMcq_blankQuestion_showsFallbackMessage() throws Exception {
            assertMalformedMcqShowsFallback("""
                    {"type":"mcq","question":"  ","options":[{"text":"3","correct":false},{"text":"4","correct":true}],"explanation":"Basic arithmetic."}""");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void malformedMcq_missingOptionText_showsFallbackMessage() throws Exception {
            assertMalformedMcqShowsFallback("""
                    {"type":"mcq","question":"What is 2+2?","options":[{"correct":false},{"text":"4","correct":true}],"explanation":"Basic arithmetic."}""");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void malformedMcqSet_emptyQuestions_showsFallbackMessage() throws Exception {
            assertMalformedMcqShowsFallback("""
                    {"type":"mcq-set","questions":[]}""");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void malformedMcqSet_oneInvalidQuestion_showsFallbackMessage() throws Exception {
            assertMalformedMcqShowsFallback(
                    """
                            {"type":"mcq-set","questions":[{"question":"Valid?","options":[{"text":"A","correct":false},{"text":"B","correct":true}],"explanation":"OK."},{"question":"Invalid?","options":[{"text":"Only one","correct":true}],"explanation":"Bad."}]}""");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void embeddedMalformedMcq_showsFallbackMessage() throws Exception {
            assertMalformedMcqShowsFallback(
                    "Here is a quiz: " + "{\"type\":\"mcq\",\"question\":\"Q?\",\"options\":[{\"text\":\"A\",\"correct\":false}],\"explanation\":\"E.\"}" + " Good luck!");
        }

        // -- MCQ response endpoint -------------------------------------------

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void saveMcqResponse_persistsResponseForSingleMcq() throws Exception {
            IrisChatSession session = createSessionForUser(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, "student1");
            IrisMessage message = saveLlmMessageWithMcqContent(session,
                    "{\"type\":\"mcq\",\"question\":\"Q?\",\"options\":[{\"text\":\"A\",\"correct\":false},{\"text\":\"B\",\"correct\":true}],\"explanation\":\"E.\"}");

            request.put(mcqResponseUrl(session, message), new IrisMcqResponseDTO(1, true, null), HttpStatus.OK);

            var json = readJsonContent(session, message);
            assertThat(json.get("response").get("selectedIndex").asInt()).isEqualTo(1);
            assertThat(json.get("response").get("submitted").asBoolean()).isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void saveMcqResponse_persistsResponseForMcqSet() throws Exception {
            IrisChatSession session = createSessionForUser(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, "student1");
            IrisMessage message = saveLlmMessageWithMcqContent(session,
                    "{\"type\":\"mcq-set\",\"questions\":[{\"question\":\"Q1?\",\"options\":[{\"text\":\"A\",\"correct\":false},{\"text\":\"B\",\"correct\":true}],\"explanation\":\"E1.\"},{\"question\":\"Q2?\",\"options\":[{\"text\":\"C\",\"correct\":true},{\"text\":\"D\",\"correct\":false}],\"explanation\":\"E2.\"}]}");

            request.put(mcqResponseUrl(session, message), new IrisMcqResponseDTO(0, false, 1), HttpStatus.OK);

            var responses = readJsonContent(session, message).get("responses");
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).get("questionIndex").asInt()).isEqualTo(1);
            assertThat(responses.get(0).get("selectedIndex").asInt()).isEqualTo(0);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void saveMcqResponse_updatesExistingResponseForMcqSet() throws Exception {
            IrisChatSession session = createSessionForUser(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, "student1");
            IrisMessage message = saveLlmMessageWithMcqContent(session,
                    "{\"type\":\"mcq-set\",\"questions\":[{\"question\":\"Q1?\",\"options\":[{\"text\":\"A\",\"correct\":false},{\"text\":\"B\",\"correct\":true}],\"explanation\":\"E1.\"},{\"question\":\"Q2?\",\"options\":[{\"text\":\"C\",\"correct\":true},{\"text\":\"D\",\"correct\":false}],\"explanation\":\"E2.\"}]}");

            request.put(mcqResponseUrl(session, message), new IrisMcqResponseDTO(0, false, 0), HttpStatus.OK);
            request.put(mcqResponseUrl(session, message), new IrisMcqResponseDTO(1, true, 0), HttpStatus.OK);

            var responses = readJsonContent(session, message).get("responses");
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).get("selectedIndex").asInt()).isEqualTo(1);
            assertThat(responses.get(0).get("submitted").asBoolean()).isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void saveMcqResponse_returns409WhenSessionMismatches() throws Exception {
            IrisChatSession session1 = createSessionForUser(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, "student1");
            IrisChatSession session2 = createSessionForUser(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, "student1");
            IrisMessage message = saveLlmMessageWithMcqContent(session1,
                    "{\"type\":\"mcq\",\"question\":\"Q?\",\"options\":[{\"text\":\"A\",\"correct\":false},{\"text\":\"B\",\"correct\":true}],\"explanation\":\"E.\"}");

            request.put(mcqResponseUrl(session2, message), new IrisMcqResponseDTO(0, true, null), HttpStatus.CONFLICT);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void saveMcqResponse_returns400ForUserMessage() throws Exception {
            IrisChatSession session = createSessionForUser(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, "student1");
            IrisMessage userMessage = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(session), session, IrisMessageSender.USER);

            request.put(mcqResponseUrl(session, userMessage), new IrisMcqResponseDTO(0, true, null), HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void saveMcqResponse_returns400ForTextOnlyLlmMessage() throws Exception {
            IrisChatSession session = createSessionForUser(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, "student1");
            IrisMessage llmTextMessage = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(session), session, IrisMessageSender.LLM);

            request.put(mcqResponseUrl(session, llmTextMessage), new IrisMcqResponseDTO(0, true, null), HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void saveMcqResponse_returns400ForNonMcqJson() throws Exception {
            IrisChatSession session = createSessionForUser(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, "student1");
            IrisMessage message = saveLlmMessageWithMcqContent(session, "{\"type\":\"other\",\"data\":\"something\"}");

            request.put(mcqResponseUrl(session, message), new IrisMcqResponseDTO(0, true, null), HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void saveMcqResponse_returns403WhenAccessingForeignSession() throws Exception {
            IrisChatSession otherUsersSession = createSessionForUser(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, "student2");
            IrisMessage message = saveLlmMessageWithMcqContent(otherUsersSession,
                    "{\"type\":\"mcq\",\"question\":\"Q?\",\"options\":[{\"text\":\"A\",\"correct\":false},{\"text\":\"B\",\"correct\":true}],\"explanation\":\"E.\"}");

            request.put(mcqResponseUrl(otherUsersSession, message), new IrisMcqResponseDTO(0, true, null), HttpStatus.FORBIDDEN);
        }

        // -- helpers for this nested class -----------------------------------

        private List<IrisMessageContent> sendAndGetLlmResponseContent(String pyrisResult) throws Exception {
            IrisChatSession session = createSessionForUser(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, "student1");
            IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(session);

            mockChatResponse(dto -> {
                assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), pyrisResult, dto.initialStages(), null, null));
                pipelineDone.set(true);
            });

            request.postWithoutResponseBody(messagesUrl(session), messageToSend, HttpStatus.CREATED);
            await().until(pipelineDone::get);

            var reloaded = irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
            var llmMessage = reloaded.getMessages().stream().filter(m -> m.getSender() == IrisMessageSender.LLM).findFirst().orElseThrow();
            return llmMessage.getContent();
        }

        private void assertMalformedMcqShowsFallback(String pyrisResult) throws Exception {
            var contents = sendAndGetLlmResponseContent(pyrisResult);
            assertThat(contents).hasSize(1);
            assertThat(((IrisTextMessageContent) contents.getFirst()).getTextContent())
                    .isEqualTo("Sorry, I tried to generate a quiz question but the response was malformed. Please try again.");
        }

        private IrisMessage saveLlmMessageWithMcqContent(IrisChatSession session, String jsonString) {
            IrisMessage message = new IrisMessage();
            IrisJsonMessageContent jsonContent = new IrisJsonMessageContent();
            jsonContent.setJsonContent(jsonString);
            message.addContent(jsonContent);
            return irisMessageService.saveMessage(message, session, IrisMessageSender.LLM);
        }

        private JsonNode readJsonContent(IrisChatSession session, IrisMessage message) {
            var reloaded = irisSessionRepository.findByIdWithMessagesAndContents(session.getId());
            var updatedMessage = reloaded.getMessages().stream().filter(m -> m.getId().equals(message.getId())).findFirst().orElseThrow();
            return ((IrisJsonMessageContent) updatedMessage.getContent().stream().filter(IrisJsonMessageContent.class::isInstance).findFirst().orElseThrow()).getJsonNode();
        }

        private String mcqResponseUrl(IrisChatSession session, IrisMessage message) {
            return messagesUrl(session) + "/" + message.getId() + "/mcq-response";
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void assertHelpfulRatingPersisted(IrisChatMode mode, Boolean helpfulValue) throws Exception {
        IrisChatSession session = createSessionForUser(mode, "student1");
        IrisMessage llmMessage = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(session), session, IrisMessageSender.LLM);

        request.putWithResponseBody(helpfulUrl(session, llmMessage), helpfulValue, IrisMessageResponseDTO.class, HttpStatus.OK);

        var reloaded = irisMessageRepository.findById(llmMessage.getId()).orElseThrow();
        assertThat(reloaded.getHelpful()).isEqualTo(helpfulValue);
    }

    private IrisChatSession createSessionForUser(IrisChatMode mode, String studentLogin) {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + studentLogin);
        IrisChatSession session = switch (mode) {
            case COURSE_CHAT -> IrisChatSessionFactory.createCourseChatSessionForUser(course, user);
            case LECTURE_CHAT -> IrisChatSessionFactory.createLectureSessionForUser(lecture, user);
            case TEXT_EXERCISE_CHAT -> IrisChatSessionFactory.createTextExerciseChatSessionForUser(textExercise, user);
            case PROGRAMMING_EXERCISE_CHAT -> IrisChatSessionFactory.createProgrammingExerciseChatSessionForUser(programmingExercise, user);
            default -> throw new IllegalArgumentException(mode + "is not a regular chat mode");
        };
        return irisChatSessionRepository.save(session);
    }

    private void mockChatResponse(Consumer<PyrisChatPipelineExecutionDTO> consumer) {
        // All four chat modes hit the same /chat/run Pyris endpoint, so a single mock variant suffices.
        irisRequestMockProvider.mockProgrammingExerciseChatResponse(consumer);
    }

    private void sendStatus(String jobId, String result, List<PyrisStageDTO> stages, String sessionTitle, List<String> suggestions) throws Exception {
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobId))));
        request.postWithoutResponseBody("/api/iris/internal/pipelines/chat/runs/" + jobId + "/status",
                new PyrisChatStatusUpdateDTO(result, stages, sessionTitle, suggestions, null, null, null), HttpStatus.OK, headers);
    }

    private static String messagesUrl(IrisChatSession session) {
        return "/api/iris/sessions/" + session.getId() + "/messages";
    }

    private static String helpfulUrl(IrisChatSession session, IrisMessage message) {
        return "/api/iris/sessions/" + session.getId() + "/messages/" + message.getId() + "/helpful";
    }
}
