package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.dto.PostDTO;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationParticipantTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.iris.service.AutonomousTutorService;
import de.tum.cit.aet.artemis.iris.service.IrisBotUserService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.autonomoustutor.PyrisAutonomousTutorPipelineStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.AutonomousTutorJob;

class AutonomousTutorServiceIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "autotutor";

    @Autowired
    private AutonomousTutorService autonomousTutorService;

    @Autowired
    private IrisBotUserService irisBotUserService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private ConversationParticipantTestRepository conversationParticipantRepository;

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    @Autowired
    private FeatureToggleService featureToggleService;

    private Course course;

    private Channel channel;

    private User student;

    private User botUser;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 1);
        course = courseUtilService.createCourse();
        channel = conversationUtilService.createCourseWideChannel(course, "general");
        irisBotUserService.ensureIrisBotUserExists();
        botUser = irisBotUserService.getIrisBotUser();
        student = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "student1").orElseThrow();
        featureToggleService.enableFeature(Feature.AutonomousTutor);
    }

    @AfterEach
    void cleanUp() throws Exception {
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

    @Test
    void handleStatusUpdate_createsAnswerPost() {
        Post post = createPostInChannel(student, "How does recursion work?");
        var job = new AutonomousTutorJob("job1", post.getId(), course.getId());
        var statusUpdate = new PyrisAutonomousTutorPipelineStatusUpdateDTO("Recursion is a technique where a function calls itself.", true, 0.9, List.of(), List.of());

        autonomousTutorService.handleStatusUpdate(job, statusUpdate);

        var botAnswers = answerPostRepository.findAnswerPostsByAuthorId(botUser.getId()).stream().filter(a -> a.getPost().getId().equals(post.getId())).toList();
        assertThat(botAnswers).hasSize(1);
        var answer = botAnswers.getFirst();
        assertThat(answer.getContent()).isEqualTo("Recursion is a technique where a function calls itself.");
    }

    @Test
    void handleStatusUpdate_botBecomesParticipant() {
        Post post = createPostInChannel(student, "What is polymorphism?");
        // Verify bot is not yet a participant
        assertThat(conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channel.getId(), botUser.getId())).isEmpty();

        var job = new AutonomousTutorJob("job2", post.getId(), course.getId());
        var statusUpdate = new PyrisAutonomousTutorPipelineStatusUpdateDTO("Polymorphism allows objects to take many forms.", true, 0.85, List.of(), List.of());

        autonomousTutorService.handleStatusUpdate(job, statusUpdate);

        assertThat(conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channel.getId(), botUser.getId())).isPresent();
    }

    @Test
    void handleStatusUpdate_sendsWebSocketForCourseWideChannel() {
        Post post = createPostInChannel(student, "Explain inheritance.");
        var job = new AutonomousTutorJob("job3", post.getId(), course.getId());
        var statusUpdate = new PyrisAutonomousTutorPipelineStatusUpdateDTO("Inheritance allows a class to inherit from another.", true, 0.9, List.of(), List.of());

        autonomousTutorService.handleStatusUpdate(job, statusUpdate);

        verify(websocketMessagingService, timeout(2000)).sendMessage(contains("/topic/metis/courses/" + course.getId()), any(PostDTO.class));
    }

    @Test
    void handleStatusUpdate_skipsWhenFeatureDisabled() {
        featureToggleService.disableFeature(Feature.AutonomousTutor);
        int initialCount = answerPostRepository.findAnswerPostsByAuthorId(botUser.getId()).size();

        Post post = createPostInChannel(student, "What is encapsulation?");
        var job = new AutonomousTutorJob("job4", post.getId(), course.getId());
        var statusUpdate = new PyrisAutonomousTutorPipelineStatusUpdateDTO("Encapsulation hides internal state.", true, 0.9, List.of(), List.of());

        autonomousTutorService.handleStatusUpdate(job, statusUpdate);

        assertThat(answerPostRepository.findAnswerPostsByAuthorId(botUser.getId())).hasSize(initialCount);
        verify(websocketMessagingService, never()).sendMessage(any(String.class), any(PostDTO.class));
    }

    @Test
    void handleStatusUpdate_skipsWhenResultNull() {
        int initialCount = answerPostRepository.findAnswerPostsByAuthorId(botUser.getId()).size();

        Post post = createPostInChannel(student, "What is abstraction?");
        var job = new AutonomousTutorJob("job5", post.getId(), course.getId());
        var statusUpdate = new PyrisAutonomousTutorPipelineStatusUpdateDTO(null, true, null, List.of(), List.of());

        autonomousTutorService.handleStatusUpdate(job, statusUpdate);

        assertThat(answerPostRepository.findAnswerPostsByAuthorId(botUser.getId())).hasSize(initialCount);
    }

    @Test
    void handleStatusUpdate_skipsWhenShouldNotPostDirectly() {
        int initialCount = answerPostRepository.findAnswerPostsByAuthorId(botUser.getId()).size();

        Post post = createPostInChannel(student, "What are design patterns?");
        var job = new AutonomousTutorJob("job6", post.getId(), course.getId());
        var statusUpdate = new PyrisAutonomousTutorPipelineStatusUpdateDTO("Design patterns are reusable solutions.", false, 0.5, List.of(), List.of());

        autonomousTutorService.handleStatusUpdate(job, statusUpdate);

        assertThat(answerPostRepository.findAnswerPostsByAuthorId(botUser.getId())).hasSize(initialCount);
    }
}
