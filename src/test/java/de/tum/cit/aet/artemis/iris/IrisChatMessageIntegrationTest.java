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
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisProgrammingExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisGlobalSettings;
import de.tum.cit.aet.artemis.iris.dto.MemirisMemoryDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.util.IrisChatSessionUtilService;
import de.tum.cit.aet.artemis.iris.util.IrisMessageFactory;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;

class IrisChatMessageIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irismessageintegration";

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private IrisSessionRepository irisSessionRepository;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private IrisChatSessionUtilService irisChatSessionUtilService;

    private ProgrammingExercise soloExercise;

    private ProgrammingExerciseStudentParticipation soloParticipation;

    private ProgrammingExercise teamExercise;

    private ProgrammingExerciseStudentParticipation teamParticipation;

    private AtomicBoolean pipelineDone;

    @BeforeEach
    void initTestCase() throws GitAPIException, IOException, URISyntaxException {
        List<User> users = userUtilService.addUsers(TEST_PREFIX, 3, 0, 0, 0);
        for (User user : users) {
            user.setSelectedLLMUsageTimestamp(ZonedDateTime.parse("2025-12-11T00:00:00Z"));
            user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
            userTestRepository.save(user);
        }

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();

        activateIrisGlobally();
        activateIrisFor(course);

        soloExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        teamExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        teamExercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(teamExercise);

        Team team = new Team();
        team.setName("Team 1");
        team.setShortName("team1");
        team.setExercise(teamExercise);
        team.setStudents(Set.of(users.get(1), users.get(2)));
        team.setOwner(users.get(1));
        final var savedTeam = teamRepository.save(team);

        Stream.of(soloExercise, teamExercise).forEach(exercise -> {
            String projectKey = exercise.getProjectKey();
            exercise.setProjectType(ProjectType.PLAIN_GRADLE);
            exercise.setTestRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + projectKey.toLowerCase() + "-tests.git");
            programmingExerciseBuildConfigRepository.save(exercise.getBuildConfig());
            programmingExerciseRepository.save(exercise);
            exercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(exercise.getId()).orElseThrow();

            // Set the correct repository URIs for the template and the solution participation.
            String templateRepositorySlug = projectKey.toLowerCase() + "-exercise";
            TemplateProgrammingExerciseParticipation templateParticipation = exercise.getTemplateParticipation();
            templateParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
            templateProgrammingExerciseParticipationRepository.save(templateParticipation);
            String solutionRepositorySlug = projectKey.toLowerCase() + "-solution";
            SolutionProgrammingExerciseParticipation solutionParticipation = exercise.getSolutionParticipation();
            solutionParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + solutionRepositorySlug + ".git");
            solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

            String assignmentRepositorySlug = projectKey.toLowerCase() + "-" + TEST_PREFIX + (exercise.isTeamMode() ? "team1" : "student1");

            // Add a participation
            ProgrammingExerciseStudentParticipation studentParticipation;
            if (exercise.isTeamMode()) {
                studentParticipation = participationUtilService.addTeamParticipationForProgrammingExercise(exercise, savedTeam);
            }
            else {
                studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
            }

            var submission = ParticipationFactory.generateProgrammingSubmission(true);
            participationUtilService.addSubmission(studentParticipation, submission);

            studentParticipation.setRepositoryUri(String.format(localVCBaseUri + "/git/%s/%s.git", projectKey, assignmentRepositorySlug));
            studentParticipation.setBranch(defaultBranch);
            programmingExerciseStudentParticipationRepository.save(studentParticipation);

            if (exercise.isTeamMode()) {
                teamParticipation = studentParticipation;
            }
            else {
                soloParticipation = studentParticipation;
            }

            // Prepare the repositories.
            try {
                localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, templateRepositorySlug);
                localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, projectKey.toLowerCase() + "-tests");
                localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, solutionRepositorySlug);
                localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, assignmentRepositorySlug);
            }
            catch (GitAPIException | IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }

            // Check that the repository folders were created in the file system for all base repositories.
            localVCLocalCITestService.verifyRepositoryFoldersExist(exercise, localVCBasePath);

            activateIrisFor(exercise);
        });

        pipelineDone = new AtomicBoolean(false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendOneMessageNormalExercise() throws Exception {
        sendOneMessage(soloExercise, "student1", soloParticipation.getSubmissions().iterator().next().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void sendOneMessageTeamExercise() throws Exception {
        sendOneMessage(teamExercise, "student2", teamParticipation.getSubmissions().iterator().next().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendOneMessageWithCustomInstructions() throws Exception {
        // Set custom instructions for programming exercise chat
        String testCustomInstructions = "Please focus on code style.";
        var exerciseSettings = irisSettingsService.getRawIrisSettingsFor(soloExercise);
        exerciseSettings.getIrisProgrammingExerciseChatSettings().setCustomInstructions(testCustomInstructions);
        irisSettingsService.saveIrisSettings(exerciseSettings);

        // Prepare session and message
        long submissionId = soloParticipation.getSubmissions().iterator().next().getId();
        IrisProgrammingExerciseChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(soloExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        messageToSend.setMessageDifferentiator(123456789);

        // Mock Pyris response and assert customInstructions in DTO
        irisRequestMockProvider.mockProgrammingExerciseChatResponseExpectingSubmissionId(dto -> {
            assertThat(dto.customInstructions()).isEqualTo(testCustomInstructions);
            pipelineDone.set(true);
        }, submissionId);

        // Send message
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.CREATED);

        // Await invocation
        await().until(pipelineDone::get);
    }

    private void sendOneMessage(ProgrammingExercise exercise, String studentLogin, long submissionId) throws Exception {
        IrisProgrammingExerciseChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(exercise,
                userUtilService.getUserByLogin(TEST_PREFIX + studentLogin));
        IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        messageToSend.setMessageDifferentiator(1453);

        irisRequestMockProvider.mockProgrammingExerciseChatResponseExpectingSubmissionId(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", dto.initialStages(), null, null));

            pipelineDone.set(true);
        }, submissionId);

        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.CREATED);

        await().until(pipelineDone::get);

        var user = userTestRepository.findByIdElseThrow(irisSession.getUserId());
        verifyWebsocketActivityWasExactly(user.getLogin(), String.valueOf(irisSession.getId()), messageDTO(messageToSend.getContent()), statusDTO(IN_PROGRESS, NOT_STARTED),
                statusDTO(DONE, IN_PROGRESS), messageDTO("Hello World"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendSuggestions() throws Exception {
        IrisProgrammingExerciseChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(soloExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        messageToSend.setMessageDifferentiator(1454);

        irisRequestMockProvider.mockProgrammingExerciseChatResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();

            List<String> suggestions = List.of("suggestion1", "suggestion2", "suggestion3");

            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), null, dto.initialStages(), null, suggestions));

            pipelineDone.set(true);
        });

        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.CREATED);

        await().until(pipelineDone::get);

        var user = userTestRepository.findByIdElseThrow(irisSession.getUserId());
        verifyWebsocketActivityWasExactly(user.getLogin(), String.valueOf(irisSession.getId()), messageDTO(messageToSend.getContent()), statusDTO(IN_PROGRESS, NOT_STARTED),
                statusDTO(DONE, IN_PROGRESS), suggestionsDTO("suggestion1", "suggestion2", "suggestion3"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendOneMessageToWrongSession() throws Exception {
        irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(soloExercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        IrisProgrammingExerciseChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(soloExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendMessageWithoutContent() throws Exception {
        IrisProgrammingExerciseChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(soloExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSession(irisSession);
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendTwoMessages() throws Exception {
        IrisProgrammingExerciseChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(soloExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        IrisMessage messageToSend1 = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);

        irisRequestMockProvider.mockProgrammingExerciseChatResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World 1", dto.initialStages(), null, null));

            pipelineDone.set(true);
        });

        irisRequestMockProvider.mockProgrammingExerciseChatResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World 2", dto.initialStages(), null, null));

            pipelineDone.set(true);
        });

        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend1, HttpStatus.CREATED);

        IrisMessage messageToSend2 = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend2, HttpStatus.CREATED);

        verify(websocketMessagingService, times(8)).sendMessageToUser(eq(TEST_PREFIX + "student1"), eq("/topic/iris/" + irisSession.getId()), any());

        var irisSessionFromDb = irisSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId());
        assertThat(irisSessionFromDb.getMessages()).hasSize(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getMessages() throws Exception {
        IrisProgrammingExerciseChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(soloExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        IrisMessage message1 = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession), irisSession, IrisMessageSender.USER);
        IrisMessage message2 = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession), irisSession, IrisMessageSender.LLM);
        IrisMessage message3 = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession), irisSession, IrisMessageSender.USER);
        IrisMessage message4 = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession), irisSession, IrisMessageSender.LLM);

        var messages = request.getList("/api/iris/sessions/" + irisSession.getId() + "/messages", HttpStatus.OK, IrisMessage.class);
        assertThat(messages).hasSize(4).containsAll(List.of(message1, message2, message3, message4));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageHelpfulTrue() throws Exception {
        IrisProgrammingExerciseChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(soloExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        IrisMessage message = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        var irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful", true, IrisMessage.class, HttpStatus.OK);
        irisMessage = irisMessageRepository.findById(irisMessage.getId()).orElseThrow();
        assertThat(irisMessage.getHelpful()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageHelpfulFalse() throws Exception {
        IrisProgrammingExerciseChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(soloExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        IrisMessage message = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        IrisMessage irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful", false, IrisMessage.class, HttpStatus.OK);
        irisMessage = irisMessageRepository.findById(irisMessage.getId()).orElseThrow();
        assertThat(irisMessage.getHelpful()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageHelpfulNull() throws Exception {
        IrisProgrammingExerciseChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(soloExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        IrisMessage message = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        IrisMessage irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful", null, IrisMessage.class, HttpStatus.OK);
        irisMessage = irisMessageRepository.findById(irisMessage.getId()).orElseThrow();
        assertThat(irisMessage.getHelpful()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageWrongSender() throws Exception {
        IrisProgrammingExerciseChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(soloExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        IrisMessage message = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        IrisMessage irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.USER);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful", true, IrisMessage.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateMessageWrongSession() throws Exception {
        IrisProgrammingExerciseChatSession irisSession1 = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(soloExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        IrisProgrammingExerciseChatSession irisSession2 = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(soloExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        IrisMessage message = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession1);
        IrisMessage irisMessage = irisMessageService.saveMessage(message, irisSession1, IrisMessageSender.USER);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession2.getId() + "/messages/" + irisMessage.getId() + "/helpful", true, IrisMessage.class, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void resendMessage() throws Exception {
        IrisProgrammingExerciseChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(soloExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);

        irisRequestMockProvider.mockProgrammingExerciseChatResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", dto.initialStages(), null, null));

            pipelineDone.set(true);
        });

        IrisMessage irisMessage = irisMessageService.saveMessage(messageToSend, irisSession, IrisMessageSender.USER);
        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/resend", null, HttpStatus.OK);
        await().until(() -> irisSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId()).getMessages().size() == 2);

        User user = userTestRepository.findByIdElseThrow(irisSession.getUserId());
        verifyWebsocketActivityWasExactly(user.getLogin(), String.valueOf(irisSession.getId()), statusDTO(IN_PROGRESS, NOT_STARTED), statusDTO(DONE, IN_PROGRESS),
                messageDTO("Hello World"));
    }

    // User needs to be Admin to change settings
    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "ADMIN")
    void sendMessageRateLimitReached() throws Exception {
        IrisProgrammingExerciseChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(soloExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student3"));
        IrisMessage messageToSend1 = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        IrisMessage messageToSend2 = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);

        irisRequestMockProvider.mockProgrammingExerciseChatResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", dto.initialStages(), null, null));

            pipelineDone.set(true);
        });

        IrisGlobalSettings globalSettings = irisSettingsService.getGlobalSettings();
        globalSettings.getIrisProgrammingExerciseChatSettings().setRateLimit(1);
        globalSettings.getIrisProgrammingExerciseChatSettings().setRateLimitTimeframeHours(10);
        irisSettingsService.saveIrisSettings(globalSettings);

        try {
            request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend1, HttpStatus.CREATED);
            await().until(() -> irisSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId()).getMessages().size() == 2);
            request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend2, HttpStatus.TOO_MANY_REQUESTS);
            var irisMessage = irisMessageService.saveMessage(messageToSend2, irisSession, IrisMessageSender.USER);
            request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/resend", null, HttpStatus.TOO_MANY_REQUESTS);

            User user = userTestRepository.findByIdElseThrow(irisSession.getUserId());
            verifyWebsocketActivityWasExactly(user.getLogin(), String.valueOf(irisSession.getId()), messageDTO(messageToSend1.getContent()), statusDTO(IN_PROGRESS, NOT_STARTED),
                    statusDTO(DONE, IN_PROGRESS), messageDTO("Hello World"));
        }
        finally {
            // Reset to not interfere with other tests
            globalSettings.getIrisProgrammingExerciseChatSettings().setRateLimit(null);
            globalSettings.getIrisProgrammingExerciseChatSettings().setRateLimitTimeframeHours(null);
            irisSettingsService.saveIrisSettings(globalSettings);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendMessageUpdatesSessionTitle() throws Exception {
        IrisProgrammingExerciseChatSession irisSession = irisChatSessionUtilService.createAndSaveProgrammingExerciseChatSessionForUser(soloExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        var messageToSend = IrisMessageFactory.createIrisMessageForSessionWithContent(irisSession);
        final String expectedTitle = "New chat";

        irisRequestMockProvider.mockProgrammingExerciseChatResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();

            assertThatNoException().isThrownBy(() -> sendStatus(dto.settings().authenticationToken(), "Hello World", dto.initialStages(), expectedTitle, null));

            pipelineDone.set(true);
        });

        request.postWithoutResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, HttpStatus.CREATED);
        await().until(pipelineDone::get);

        var irisSessionFromDb = irisSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId());
        assertThat(irisSessionFromDb.getTitle()).isEqualTo(expectedTitle);
    }

    private void sendStatus(String jobId, String result, List<PyrisStageDTO> stages, String sessionTitle, List<String> suggestions) throws Exception {
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobId))));
        request.postWithoutResponseBody("/api/iris/internal/pipelines/programming-exercise-chat/runs/" + jobId + "/status",
                new PyrisChatStatusUpdateDTO(result, stages, sessionTitle, suggestions, null,
                        List.of(new MemirisMemoryDTO("UUID", "Test Memory", "Test Content", Collections.emptyList(), Collections.emptyList(), false, false)), null),
                HttpStatus.OK, headers);
    }
}
