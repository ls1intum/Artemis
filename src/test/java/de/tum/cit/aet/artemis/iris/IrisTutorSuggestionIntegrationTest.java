package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.DisplayPriority;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationParticipantTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisTutorSuggestionSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

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
    private CourseTestRepository courseRepository;

    @Autowired
    private IrisMessageService irisMessageService;

    private ProgrammingExercise exercise;

    private Course course;

    private AtomicBoolean pipelineDone;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        course = courseRepository.save(course);
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
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
        assertThat(actualIrisSession.getUser()).isEqualTo(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        assertThat(post).isEqualTo(actualIrisSession.getPost());
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
        message.setSender(IrisMessageSender.TUT_SUG);
        message.setSession(irisSession);
        irisMessageService.saveMessage(message, irisSession, IrisMessageSender.TUT_SUG);
        var messages = irisMessageRepository.findAllBySessionId(irisSession.getId());
        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().getContent().getFirst().toString()).contains("Test tutor suggestion request");
        assertThat(messages.getFirst().getSender()).isEqualTo(IrisMessageSender.TUT_SUG);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateTutorSuggestionShouldProcessMessageAndReturnResponse() throws Exception {
        var post = createPostInExerciseChat(exercise, TEST_PREFIX);
        var irisSession = request.postWithResponseBody(tutorSuggestionUrl(post.getId()), null, IrisSession.class, HttpStatus.CREATED);
        var message = new IrisMessage();
        message.addContent(new IrisTextMessageContent("Test tutor suggestion request"));
        message.setSender(IrisMessageSender.TUT_SUG);
        message.setSession(irisSession);
        irisRequestMockProvider.mockTutorSuggestionResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();

            pipelineDone.set(true);
        });

        var response = request.postWithResponseBody(irisSessionUrl(post.getId()) + "/messages", message, IrisMessage.class, HttpStatus.CREATED);
        await().atMost(java.time.Duration.ofSeconds(5)).until(pipelineDone::get);
        assertThat(response.getContent().getFirst().toString()).contains("Test tutor suggestion request");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testGetTutorSuggestionSessionAsStudent() throws Exception {
        var post = createPostInExerciseChat(exercise, TEST_PREFIX);
        request.postWithResponseBody(tutorSuggestionUrl(post.getId()), null, IrisSession.class, HttpStatus.FORBIDDEN);
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
        post.setCourse(course);
        post = postRepository.save(post);
        return post;
    }

}
