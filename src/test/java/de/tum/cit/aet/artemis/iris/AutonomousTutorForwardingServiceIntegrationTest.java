package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.CreatedConversationMessage;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.service.ConversationMessagingService;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.iris.service.AutonomousTutorForwardingService;
import de.tum.cit.aet.artemis.iris.service.IrisBotUserService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.autonomoustutor.PyrisAutonomousTutorPipelineExecutionDTO;

class AutonomousTutorForwardingServiceIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "autotutorfwd";

    @Autowired
    private AutonomousTutorForwardingService autonomousTutorForwardingService;

    @Autowired
    private ConversationMessagingService conversationMessagingService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private IrisBotUserService irisBotUserService;

    @Autowired
    private FeatureToggleService featureToggleService;

    private Course course;

    private Channel channel;

    private User student;

    private User botUser;

    private User student2;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 1);
        course = courseUtilService.createCourse();
        channel = conversationUtilService.createCourseWideChannel(course, "general");
        irisBotUserService.ensureIrisBotUserExists();
        botUser = irisBotUserService.getIrisBotUser();
        student = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "student1").orElseThrow();
        student2 = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "student2").orElseThrow();
        enableIrisFor(course);
        featureToggleService.enableFeature(Feature.AutonomousTutor);
    }

    @AfterEach
    void cleanUp() {
        answerPostRepository.deleteAll(answerPostRepository.findAnswerPostsByAuthorId(botUser.getId()));
        featureToggleService.disableFeature(Feature.AutonomousTutor);
    }

    private Post createPostInChannel(User author, String content) {
        Post post = new Post();
        post.setAuthor(author);
        post.setContent(content);
        post.setConversation(channel);
        post.setVisibleForStudents(true);
        return conversationMessageRepository.save(post);
    }

    private AnswerPost createAnswerPost(Post parent, User author, String content) {
        AnswerPost answer = new AnswerPost();
        answer.setPost(parent);
        answer.setAuthor(author);
        answer.setContent(content);
        answer.setResolvesPost(false);
        AnswerPost saved = answerPostRepository.save(answer);
        parent.addAnswerPost(saved);
        return saved;
    }

    // --- AutonomousTutorForwardingService.onNewMessage() tests ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void onNewMessage_forwardsToPyris() {
        Post post = createPostInChannel(student, "How does polymorphism work?");
        channel.setCourse(course);

        AtomicBoolean pipelineCalled = new AtomicBoolean(false);
        irisRequestMockProvider.mockAutonomousTutorResponse(dto -> pipelineCalled.set(true));

        autonomousTutorForwardingService.onNewMessage(post, channel, course);

        await().atMost(Duration.ofSeconds(5)).until(pipelineCalled::get);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void onNewMessage_skipsWhenFeatureDisabled() {
        featureToggleService.disableFeature(Feature.AutonomousTutor);
        Post post = createPostInChannel(student, "What is abstraction?");
        channel.setCourse(course);

        AtomicBoolean pipelineCalled = new AtomicBoolean(false);
        irisRequestMockProvider.mockAutonomousTutorResponse(dto -> pipelineCalled.set(true));

        // onNewMessage returns early synchronously — no pipeline submitted
        autonomousTutorForwardingService.onNewMessage(post, channel, course);

        assertThat(pipelineCalled).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void onNewMessage_skipsWhenIrisNotEnabledForCourse() {
        disableIrisFor(course);
        Post post = createPostInChannel(student, "What is encapsulation?");
        channel.setCourse(course);

        AtomicBoolean pipelineCalled = new AtomicBoolean(false);
        irisRequestMockProvider.mockAutonomousTutorResponse(dto -> pipelineCalled.set(true));

        autonomousTutorForwardingService.onNewMessage(post, channel, course);

        assertThat(pipelineCalled).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void onNewMessage_skipsWhenUserChoseNoAi() {
        student.setSelectedLLMUsage(AiSelectionDecision.NO_AI);
        userTestRepository.save(student);

        Post post = createPostInChannel(student, "Explain recursion.");
        channel.setCourse(course);

        AtomicBoolean pipelineCalled = new AtomicBoolean(false);
        irisRequestMockProvider.mockAutonomousTutorResponse(dto -> pipelineCalled.set(true));

        autonomousTutorForwardingService.onNewMessage(post, channel, course);

        assertThat(pipelineCalled).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void onNewMessage_skipsForBotMessages() {
        Post post = createPostInChannel(botUser, "I am the bot.");
        channel.setCourse(course);

        AtomicBoolean pipelineCalled = new AtomicBoolean(false);
        irisRequestMockProvider.mockAutonomousTutorResponse(dto -> pipelineCalled.set(true));

        autonomousTutorForwardingService.onNewMessage(post, channel, course);

        assertThat(pipelineCalled).isFalse();
    }

    // --- AutonomousTutorForwardingService.onNewAnswerMessage() tests ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void onNewAnswerMessage_forwardsToPyris() {
        Post post = createPostInChannel(student, "How does polymorphism work?");
        AnswerPost answerPost = createAnswerPost(post, student2, "It allows different types to be used through a common interface.");
        channel.setCourse(course);

        AtomicBoolean pipelineCalled = new AtomicBoolean(false);
        irisRequestMockProvider.mockAutonomousTutorResponse(dto -> pipelineCalled.set(true));

        autonomousTutorForwardingService.onNewAnswerMessage(answerPost, post, channel, course);

        await().atMost(Duration.ofSeconds(5)).until(pipelineCalled::get);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void onNewAnswerMessage_skipsWhenReplyAuthorChoseNoAi() {
        Post post = createPostInChannel(student, "What is inheritance?");
        student2.setSelectedLLMUsage(AiSelectionDecision.NO_AI);
        userTestRepository.save(student2);
        AnswerPost answerPost = createAnswerPost(post, student2, "It is a mechanism for code reuse.");
        channel.setCourse(course);

        AtomicBoolean pipelineCalled = new AtomicBoolean(false);
        irisRequestMockProvider.mockAutonomousTutorResponse(dto -> pipelineCalled.set(true));

        autonomousTutorForwardingService.onNewAnswerMessage(answerPost, post, channel, course);

        assertThat(pipelineCalled).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void onNewAnswerMessage_skipsWhenParentPostAuthorChoseNoAi() {
        student.setSelectedLLMUsage(AiSelectionDecision.NO_AI);
        userTestRepository.save(student);
        Post post = createPostInChannel(student, "Explain encapsulation.");
        AnswerPost answerPost = createAnswerPost(post, student2, "It hides internal state.");
        channel.setCourse(course);

        AtomicBoolean pipelineCalled = new AtomicBoolean(false);
        irisRequestMockProvider.mockAutonomousTutorResponse(dto -> pipelineCalled.set(true));

        autonomousTutorForwardingService.onNewAnswerMessage(answerPost, post, channel, course);

        assertThat(pipelineCalled).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void onNewAnswerMessage_skipsForBotReplies() {
        Post post = createPostInChannel(student, "What is abstraction?");
        AnswerPost botAnswer = createAnswerPost(post, botUser, "I am the bot.");
        channel.setCourse(course);

        AtomicBoolean pipelineCalled = new AtomicBoolean(false);
        irisRequestMockProvider.mockAutonomousTutorResponse(dto -> pipelineCalled.set(true));

        autonomousTutorForwardingService.onNewAnswerMessage(botAnswer, post, channel, course);

        assertThat(pipelineCalled).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void onNewAnswerMessage_skipsWhenFeatureDisabled() {
        featureToggleService.disableFeature(Feature.AutonomousTutor);
        Post post = createPostInChannel(student, "Explain recursion.");
        AnswerPost answerPost = createAnswerPost(post, student2, "A function calling itself.");
        channel.setCourse(course);

        AtomicBoolean pipelineCalled = new AtomicBoolean(false);
        irisRequestMockProvider.mockAutonomousTutorResponse(dto -> pipelineCalled.set(true));

        autonomousTutorForwardingService.onNewAnswerMessage(answerPost, post, channel, course);

        assertThat(pipelineCalled).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void onNewAnswerMessage_skipsWhenIrisNotEnabledForCourse() {
        disableIrisFor(course);
        Post post = createPostInChannel(student, "What is a lambda?");
        AnswerPost answerPost = createAnswerPost(post, student2, "An anonymous function.");
        channel.setCourse(course);

        AtomicBoolean pipelineCalled = new AtomicBoolean(false);
        irisRequestMockProvider.mockAutonomousTutorResponse(dto -> pipelineCalled.set(true));

        autonomousTutorForwardingService.onNewAnswerMessage(answerPost, post, channel, course);

        assertThat(pipelineCalled).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void onNewAnswerMessage_redactsNoAiRepliesInThreadContext() {
        Post post = createPostInChannel(student, "How do generics work?");

        // student2 opts out of AI and replies first
        student2.setSelectedLLMUsage(AiSelectionDecision.NO_AI);
        userTestRepository.save(student2);
        createAnswerPost(post, student2, "This should be redacted.");

        // student (AI-consenting) replies — this triggers the pipeline
        AnswerPost triggeringReply = createAnswerPost(post, student, "Can you clarify the bounds?");
        channel.setCourse(course);

        AtomicReference<PyrisAutonomousTutorPipelineExecutionDTO> capturedDto = new AtomicReference<>();
        irisRequestMockProvider.mockAutonomousTutorResponse(capturedDto::set);

        autonomousTutorForwardingService.onNewAnswerMessage(triggeringReply, post, channel, course);

        await().atMost(Duration.ofSeconds(5)).until(() -> capturedDto.get() != null);

        var answers = capturedDto.get().post().answers();
        assertThat(answers).hasSize(2);

        var redactedAnswer = answers.stream().filter(a -> a.redacted()).findFirst();
        assertThat(redactedAnswer).isPresent();
        assertThat(redactedAnswer.get().content()).isNull();

        var visibleAnswer = answers.stream().filter(a -> !a.redacted()).findFirst();
        assertThat(visibleAnswer).isPresent();
        assertThat(visibleAnswer.get().content()).isEqualTo("Can you clarify the bounds?");
    }

    // --- Integration test: ConversationMessagingService wires the forwarding service ---

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void notifyAboutMessageCreation_forwardsToPyris() {
        Post post = createPostInChannel(student, "Can you explain inheritance?");
        channel.setCourse(course);

        AtomicBoolean pipelineCalled = new AtomicBoolean(false);
        irisRequestMockProvider.mockAutonomousTutorResponse(dto -> pipelineCalled.set(true));

        // Trigger via the messaging service (async) - this tests the wiring
        conversationMessagingService.notifyAboutMessageCreation(new CreatedConversationMessage(post, channel, Set.of()));

        await().atMost(Duration.ofSeconds(5)).until(pipelineCalled::get);
    }
}
