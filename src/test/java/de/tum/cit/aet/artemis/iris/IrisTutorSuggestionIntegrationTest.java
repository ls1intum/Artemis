package de.tum.cit.aet.artemis.iris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.DisplayPriority;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationParticipantTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisTutorSuggestionSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.TutorSuggestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.web.open.PublicPyrisStatusUpdateResource;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@ActiveProfiles(PROFILE_IRIS)
class IrisTutorSuggestionIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "iristutorsuggestionintegration";

    @Autowired
    private IrisTutorSuggestionSessionRepository irisTutorSuggestionSessionRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    protected UserTestRepository userTestRepository;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    @Autowired
    private ConversationParticipantTestRepository conversationParticipantRepository;

    @Autowired
    private PostTestRepository postRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private PublicPyrisStatusUpdateResource publicPyrisStatusUpdateResource;

    private ProgrammingExercise exercise;

    private Course course;

    private AtomicBoolean pipelineDone;

    @Autowired
    private PyrisJobService pyrisJobService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        course = courseRepository.save(course);
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(exercise);

        pipelineDone = new AtomicBoolean(false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createSession() throws Exception {
        var post = createPostInExerciseChat(exercise, TEST_PREFIX);
        var irisSession = request.postWithResponseBody(tutorSuggestionUrl(post.getId()), null, IrisTutorSuggestionSession.class, HttpStatus.CREATED);
        var actualIrisSession = irisTutorSuggestionSessionRepository.findByIdElseThrow(irisSession.getId());
        assertThat(actualIrisSession.getUserId()).isEqualTo(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1").getId());
        assertThat(post.getId()).isEqualTo(actualIrisSession.getPostId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateTutorSuggestionSessionShouldReturnDifferentResponsesForSamePost() throws Exception {
        var post = createPostInExerciseChat(exercise, TEST_PREFIX);
        var firstResponse = request.postWithResponseBody(tutorSuggestionUrl(post.getId()), null, IrisSession.class, HttpStatus.CREATED);
        var secondResponse = request.postWithResponseBody(tutorSuggestionUrl(post.getId()), null, IrisSession.class, HttpStatus.CREATED);
        assertThat(firstResponse).isNotEqualTo(secondResponse);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getCurrentSession() throws Exception {
        var post = createPostInExerciseChat(exercise, TEST_PREFIX);
        var irisSession = request.postWithResponseBody(tutorSuggestionUrl(post.getId()), null, IrisSession.class, HttpStatus.CREATED);
        var currentIrisSession = request.postWithResponseBody(tutorSuggestionUrl(post.getId()) + "/current", null, IrisSession.class, HttpStatus.OK);
        assertThat(irisSession).isEqualTo(currentIrisSession);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getCurrentMessagesForSession() throws Exception {
        var post = createPostInExerciseChat(exercise, TEST_PREFIX);
        var irisSession = request.postWithResponseBody(tutorSuggestionUrl(post.getId()), null, IrisSession.class, HttpStatus.CREATED);
        var message = new IrisMessage();
        message.addContent(new IrisTextMessageContent("Test tutor suggestion request"));
        message.setSender(IrisMessageSender.LLM);
        message.setSession(irisSession);
        irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        var messages = irisMessageRepository.findAllBySessionId(irisSession.getId());
        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().getContent().getFirst().toString()).contains("Test tutor suggestion request");
        assertThat(messages.getFirst().getSender()).isEqualTo(IrisMessageSender.LLM);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateTutorSuggestionShouldProcessRequestAndReturnResponse() throws Exception {
        var post = createPostInExerciseChat(exercise, TEST_PREFIX);
        var irisSession = request.postWithResponseBody(tutorSuggestionUrl(post.getId()), null, IrisSession.class, HttpStatus.CREATED);
        irisRequestMockProvider.mockTutorSuggestionResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();

            pipelineDone.set(true);
        });

        request.postWithResponseBody(irisSessionUrl(irisSession.getId()) + "/tutor-suggestion", null, Void.class, HttpStatus.CREATED);
        await().atMost(java.time.Duration.ofSeconds(5)).until(pipelineDone::get);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testGetTutorSuggestionSessionAsStudent() throws Exception {
        var post = createPostInExerciseChat(exercise, TEST_PREFIX);
        request.postWithResponseBody(tutorSuggestionUrl(post.getId()), null, IrisSession.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testExecuteTutorSuggestionPipelineShouldSendStatusUpdates() throws Exception {
        var post = createPostInExerciseChat(exercise, TEST_PREFIX);
        var conversation = post.getConversation();
        conversation.setCourse(course);
        post = postRepository.saveAndFlush(post);
        var irisSession = request.postWithResponseBody(tutorSuggestionUrl(post.getId()), null, IrisTutorSuggestionSession.class, HttpStatus.CREATED);
        irisSession.setPostId(post.getId());

        pipelineDone.set(false);
        Post finalPost = post;
        irisRequestMockProvider.mockTutorSuggestionResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
            assertThat(dto.post()).isNotNull();
            assertThat(dto.post().id()).isEqualTo(finalPost.getId());
            assertThat(dto.post().content()).isEqualTo(finalPost.getContent());
            pipelineDone.set(true);
        });

        request.postWithResponseBody(irisSessionUrl(irisSession.getId()) + "/tutor-suggestion", null, Void.class, HttpStatus.CREATED);
        await().atMost(java.time.Duration.ofSeconds(5)).until(pipelineDone::get);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTutorSuggestionPipelineShouldIncludeAnswerPosts() throws Exception {
        var post = createPostInExerciseChat(exercise, TEST_PREFIX);
        var conversation = post.getConversation();
        conversation.setCourse(course);
        post = postRepository.save(post);

        // Add an answer post to the post
        var answerPost = new AnswerPost();
        answerPost.setContent("Answer content");
        answerPost.setPost(post);
        answerPost.setAuthor(userTestRepository.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow());
        answerPost = answerPostRepository.save(answerPost);
        post.addAnswerPost(answerPost);
        post = postRepository.save(post);

        var irisSession = request.postWithResponseBody(tutorSuggestionUrl(post.getId()), null, IrisTutorSuggestionSession.class, HttpStatus.CREATED);
        irisSession.setPostId(post.getId());

        pipelineDone.set(false);
        irisRequestMockProvider.mockTutorSuggestionResponse(dto -> {
            // Check that the answer posts are included in the DTO
            assertThat(dto.post().answers()).isNotEmpty();
            pipelineDone.set(true);
        });

        request.postWithResponseBody(irisSessionUrl(irisSession.getId()) + "/tutor-suggestion", null, Void.class, HttpStatus.CREATED);
        await().atMost(java.time.Duration.ofSeconds(5)).until(pipelineDone::get);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTutorSuggestionStatusUpdateShouldBeHandled() throws Exception {
        var post = createPostInExerciseChat(exercise, TEST_PREFIX);
        var conversation = post.getConversation();
        conversation.setCourse(course);
        post = postRepository.save(post);

        var irisSession = request.postWithResponseBody(tutorSuggestionUrl(post.getId()), null, IrisTutorSuggestionSession.class, HttpStatus.CREATED);
        irisSession.setPostId(post.getId());

        String token = pyrisJobService.addTutorSuggestionJob(post.getId(), course.getId(), irisSession.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + token);
        // Manually authenticate the job to verify token registration
        var mockRequest = new org.springframework.mock.web.MockHttpServletRequest();
        mockRequest.addHeader("Authorization", "Bearer " + token);
        var job = pyrisJobService.getAndAuthenticateJobFromHeaderElseThrow(mockRequest, de.tum.cit.aet.artemis.iris.service.pyris.job.TutorSuggestionJob.class);
        assertThat(job).isNotNull();
        assertThat(job.jobId()).isEqualTo(token);
        List<PyrisStageDTO> stages = List.of(new PyrisStageDTO("Test stage", 0, PyrisStageState.DONE, "Done"));
        var statusUpdate = new TutorSuggestionStatusUpdateDTO("Test suggestion", "Test result", stages, null);
        var mockRequestForStatusUpdate = new org.springframework.mock.web.MockHttpServletRequest();
        mockRequestForStatusUpdate.addHeader("Authorization", "Bearer " + token);
        publicPyrisStatusUpdateResource.setTutorSuggestionJobStatus(token, statusUpdate, mockRequestForStatusUpdate);

        // Remove the job and assert that accessing it throws an exception
        var requestAfterRemoval = new org.springframework.mock.web.MockHttpServletRequest();
        requestAfterRemoval.addHeader("Authorization", "Bearer " + token);
        assertThatThrownBy(
                () -> pyrisJobService.getAndAuthenticateJobFromHeaderElseThrow(requestAfterRemoval, de.tum.cit.aet.artemis.iris.service.pyris.job.TutorSuggestionJob.class))
                .isInstanceOf(de.tum.cit.aet.artemis.core.exception.AccessForbiddenException.class).hasMessageContaining("No valid token provided");

        // Check if the messages where saved
        var messages = irisMessageRepository.findAllBySessionId(irisSession.getId());
        assertThat(messages).hasSize(2);
        assertThat(messages.getFirst().getContent().getFirst().toString()).contains("Test suggestion");
        assertThat(messages.getFirst().getSender()).isEqualTo(IrisMessageSender.ARTIFACT);
        assertThat(messages.get(1).getContent().getFirst().toString()).contains("Test result");
        assertThat(messages.get(1).getSender()).isEqualTo(IrisMessageSender.LLM);
    }

    private static String tutorSuggestionUrl(long sessionId) {
        return "/api/iris/tutor-suggestion/" + sessionId + "/sessions";
    }

    private static String irisSessionUrl(long sessionId) {
        return "/api/iris/sessions/" + sessionId;
    }

    private Post createPostInExerciseChat(ProgrammingExercise exercise, String userPrefix) {
        var student = userTestRepository.findOneByLogin(userPrefix + "student1").orElseThrow();
        var tutor = userTestRepository.findOneByLogin(userPrefix + "tutor1").orElseThrow();
        var chat = new Channel();
        chat.setExercise(exercise);
        chat.setName("Test channel");
        chat.setCourse(course);
        chat = channelRepository.save(chat);
        var participant1 = new ConversationParticipant();
        participant1.setConversation(chat);
        participant1.setUser(student);
        conversationParticipantRepository.save(participant1);
        var participant2 = new ConversationParticipant();
        participant2.setConversation(chat);
        participant2.setUser(tutor);
        conversationParticipantRepository.save(participant2);
        chat = channelRepository.findById(chat.getId()).orElseThrow();
        Post post = new Post();
        post.setAuthor(student);
        post.setDisplayPriority(DisplayPriority.NONE);
        post.setConversation(chat);
        post.setContent("Test content");
        post = postRepository.save(post);
        return post;
    }

}
