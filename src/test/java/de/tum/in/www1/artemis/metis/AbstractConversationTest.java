package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationMessageRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.GroupChatRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.OneToOneChatRepository;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ChannelDTO;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.GroupChatDTO;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.OneToOneChatDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.ConversationWebsocketDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;

/**
 * Contains useful methods for testing the channel feature
 */
abstract class AbstractConversationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ConversationRepository conversationRepository;

    @Autowired
    ConversationParticipantRepository conversationParticipantRepository;

    @Autowired
    ChannelRepository channelRepository;

    @Autowired
    GroupChatRepository groupChatRepository;

    @Autowired
    OneToOneChatRepository oneToOneChatRepository;

    @Autowired
    SimpMessageSendingOperations messagingTemplate;

    @Autowired
    ConversationMessageRepository conversationMessageRepository;

    Long exampleCourseId;

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    @BeforeEach
    void setupTestScenario() throws Exception {
        // creating the users student1-student20, tutor1-tutor10, editor1-editor10 and instructor1-instructor10
        this.database.addUsers(20, 10, 10, 10);

        // Add users that are not in the course
        userRepository.save(ModelFactory.generateActivatedUser("student42"));
        userRepository.save(ModelFactory.generateActivatedUser("tutor42"));
        userRepository.save(ModelFactory.generateActivatedUser("editor42"));
        userRepository.save(ModelFactory.generateActivatedUser("instructor42"));

        var course = this.database.createCourse();
        courseRepository.save(course);

        exampleCourseId = course.getId();
    }

    Set<ConversationParticipant> getParticipants(Long conversationId) {
        return conversationParticipantRepository.findConversationParticipantByConversationId(conversationId);
    }

    void postInConversation(Long conversationId, String authorLogin) throws Exception {
        Post postToSave = createPostWithConversation(conversationId, authorLogin);

        Post createdPost = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/messages", postToSave, Post.class, HttpStatus.CREATED);
        assertThat(createdPost.getConversation().getId()).isEqualTo(conversationId);

        PostContextFilter postContextFilter = new PostContextFilter();
        postContextFilter.setConversationId(createdPost.getConversation().getId());
        assertThat(conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged())).hasSize(1);
    }

    Post createPostWithConversation(Long conversationId, String authorLogin) {
        Post post = new Post();
        post.setAuthor(database.getUserByLogin(authorLogin));
        post.setDisplayPriority(DisplayPriority.NONE);
        var conv = conversationRepository.findByIdElseThrow(conversationId);
        post.setConversation(conv);
        return post;
    }

    Set<ConversationParticipant> assertParticipants(Long conversationId, int expectedSize, String... expectedUserLogins) {
        var participants = this.getParticipants(conversationId);
        assertThat(participants).hasSize(expectedSize);
        assertThat(participants).extracting(ConversationParticipant::getUser).extracting(User::getLogin).containsExactlyInAnyOrder(expectedUserLogins);
        return participants;
    }

    void verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction crudAction, Long conversationId, String... expectedUserLogins) {
        for (String expectedUserLogin : expectedUserLogins) {
            verifyParticipantTopicWebsocketSent(crudAction, conversationId, expectedUserLogin);
        }
    }

    void verifyParticipantTopicWebsocketSent(MetisCrudAction crudAction, Long conversationId, String receivingUserLogin) {
        var receivingUser = database.getUserByLogin(receivingUserLogin);
        var topic = ConversationService.getConversationParticipantTopicName(exampleCourseId) + receivingUser.getId();
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(receivingUserLogin), eq(topic),
                argThat((argument) -> argument instanceof ConversationWebsocketDTO && ((ConversationWebsocketDTO) argument).getCrudAction().equals(crudAction)
                        && ((ConversationWebsocketDTO) argument).getConversation().getId().equals(conversationId)));

    }

    void verifyNoParticipantTopicWebsocketSent() {
        verify(this.messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(ConversationWebsocketDTO.class));
    }

    void verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction... actions) {
        verify(this.messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(),
                argThat((argument) -> argument instanceof ConversationWebsocketDTO && !Arrays.asList(actions).contains(((ConversationWebsocketDTO) argument).getCrudAction())));
    }

    void assertUsersAreConversationMembers(Long channelId, String... userLogin) {
        var conversationMembers = getParticipants(channelId).stream().map(ConversationParticipant::getUser);
        assertThat(conversationMembers).extracting(User::getLogin).contains(userLogin);
    }

    void assertUserAreNotConversationMembers(Long channelId, String... userLogin) {
        var conversationMembers = getParticipants(channelId).stream().map(ConversationParticipant::getUser);
        assertThat(conversationMembers).extracting(User::getLogin).doesNotContain(userLogin);
    }

    void removeUsersFromConversation(Long conversationId, String... userLogin) throws Exception {
        for (String login : userLogin) {
            var user = database.getUserByLogin(login);
            var participant = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(conversationId, user.getId());
            participant.ifPresent(conversationParticipant -> conversationParticipantRepository.delete(conversationParticipant));
        }
    }

    ChannelDTO createChannel(boolean isPublicChannel) throws Exception {
        return createChannel(isPublicChannel, "general");
    }

    ChannelDTO createChannel(boolean isPublicChannel, String name) throws Exception {
        var channelDTO = new ChannelDTO();
        channelDTO.setName(name);
        channelDTO.setIsPublic(isPublicChannel);
        channelDTO.setDescription("general channel");

        var chat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/channels", channelDTO, ChannelDTO.class, HttpStatus.CREATED);
        resetWebsocketMock();
        return chat;
    }

    GroupChatDTO createGroupChat(String... userLogins) throws Exception {
        var chat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/group-chats/", Arrays.stream(userLogins).toList(), GroupChatDTO.class, HttpStatus.CREATED);
        this.resetWebsocketMock();
        return chat;
    }

    OneToOneChatDTO createAndPostInOneToOneChat(String withUserLogin) throws Exception {
        var chat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats/", List.of(withUserLogin), OneToOneChatDTO.class, HttpStatus.CREATED);
        this.postInConversation(chat.getId(), "student1");
        this.resetWebsocketMock();
        return chat;
    }

    void addUsersToConversation(Long conversationId, String... userLogin) throws Exception {
        for (String login : userLogin) {
            var user = database.getUserByLogin(login);
            var existing = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(conversationId, user.getId());
            if (existing.isPresent()) {
                continue;
            }
            var participant = new ConversationParticipant();
            participant.setConversation(conversationRepository.findByIdElseThrow(conversationId));
            participant.setIsAdmin(false);
            participant.setUser(user);
            conversationParticipantRepository.save(participant);
        }
    }

    void resetWebsocketMock() {
        reset(this.messagingTemplate);
    }

}
