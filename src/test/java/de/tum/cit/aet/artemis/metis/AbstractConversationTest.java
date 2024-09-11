package de.tum.cit.aet.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.DisplayPriority;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ConversationRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.GroupChatRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.OneToOneChatRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.course.CourseUtilService;
import de.tum.cit.aet.artemis.user.UserUtilService;
import de.tum.cit.aet.artemis.web.rest.dto.PostContextFilterDTO;
import de.tum.cit.aet.artemis.communication.web.conversation.dtos.ChannelDTO;
import de.tum.cit.aet.artemis.communication.web.conversation.dtos.GroupChatDTO;
import de.tum.cit.aet.artemis.web.websocket.dto.metis.ConversationWebsocketDTO;
import de.tum.cit.aet.artemis.web.websocket.dto.metis.MetisCrudAction;

/**
 * Contains useful methods for testing the conversations futures
 */
abstract class AbstractConversationTest extends AbstractSpringIntegrationIndependentTest {

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
    ConversationMessageRepository conversationMessageRepository;

    @Autowired
    UserUtilService userUtilService;

    @Autowired
    CourseUtilService courseUtilService;

    Long exampleCourseId;

    Course exampleCourse;

    String testPrefix = "";

    @BeforeEach
    void setupTestScenario() throws Exception {
        this.testPrefix = getTestPrefix();
        var course = courseUtilService.createCourseWithMessagingEnabled();
        exampleCourse = courseRepository.save(course);
        exampleCourseId = exampleCourse.getId();
    }

    abstract String getTestPrefix();

    Set<ConversationParticipant> getParticipants(Long conversationId) {
        return conversationParticipantRepository.findConversationParticipantsByConversationId(conversationId);
    }

    Post postInConversation(Long conversationId, String authorLoginWithoutPrefix) throws Exception {
        PostContextFilterDTO postContextFilter = new PostContextFilterDTO(exampleCourseId, null, null, conversationId, null, false, false, false, null, null);
        var requestingUser = userRepository.getUser();

        var numberBefore = conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged(), requestingUser.getId()).stream().toList().size();
        Post postToSave = createPostWithConversation(conversationId, authorLoginWithoutPrefix);

        Post createdPost = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/messages", postToSave, Post.class, HttpStatus.CREATED);
        assertThat(createdPost.getConversation().getId()).isEqualTo(conversationId);
        assertThat(conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged(), requestingUser.getId())).hasSize(numberBefore + 1);
        return createdPost;
    }

    Post createPostWithConversation(Long conversationId, String authorLoginWithoutPrefix) {
        Post post = new Post();
        post.setAuthor(userUtilService.getUserByLogin(testPrefix + authorLoginWithoutPrefix));
        post.setDisplayPriority(DisplayPriority.NONE);
        var conv = conversationRepository.findByIdElseThrow(conversationId);
        post.setConversation(conv);
        return post;
    }

    Set<ConversationParticipant> assertParticipants(Long conversationId, int expectedSize, String... expectedUserLoginsWithoutPrefix) {
        var expectedUserLoginsWithPrefix = Arrays.stream(expectedUserLoginsWithoutPrefix).map(login -> testPrefix + login).toArray(String[]::new);
        var participants = this.getParticipants(conversationId);
        assertThat(participants).hasSize(expectedSize);
        assertThat(participants).extracting(ConversationParticipant::getUser).extracting(User::getLogin).containsExactlyInAnyOrder(expectedUserLoginsWithPrefix);
        return participants;
    }

    void verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction crudAction, Long conversationId, String... userLoginsWithoutPrefix) {
        for (String userLoginWithoutPrefix : userLoginsWithoutPrefix) {
            verifyParticipantTopicWebsocketSent(crudAction, conversationId, userLoginWithoutPrefix);
        }
    }

    void verifyParticipantTopicWebsocketSent(MetisCrudAction crudAction, Long conversationId, String userLoginsWithoutPrefix) {
        var receivingUser = userUtilService.getUserByLogin(testPrefix + userLoginsWithoutPrefix);
        var topic = ConversationService.getConversationParticipantTopicName(exampleCourseId) + receivingUser.getId();
        verify(websocketMessagingService, timeout(2000)).sendMessageToUser(eq(testPrefix + userLoginsWithoutPrefix), eq(topic),
                argThat((argument) -> argument instanceof ConversationWebsocketDTO && ((ConversationWebsocketDTO) argument).action().equals(crudAction)
                        && ((ConversationWebsocketDTO) argument).conversation().getId().equals(conversationId)));

    }

    void verifyNoParticipantTopicWebsocketSent() {
        verify(this.websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(ConversationWebsocketDTO.class));
    }

    void verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction... actions) {
        verify(this.websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(),
                argThat((argument) -> argument instanceof ConversationWebsocketDTO && !Arrays.asList(actions).contains(((ConversationWebsocketDTO) argument).action())));
    }

    void assertUsersAreConversationMembers(Long channelId, String... userLoginsWithoutPrefix) {
        var expectedUserLoginsWithPrefix = Arrays.stream(userLoginsWithoutPrefix).map(login -> testPrefix + login).toArray(String[]::new);
        var conversationMembers = getParticipants(channelId).stream().map(ConversationParticipant::getUser);
        assertThat(conversationMembers).extracting(User::getLogin).contains(expectedUserLoginsWithPrefix);
    }

    void assertUserAreNotConversationMembers(Long channelId, String... userLoginsWithoutPrefix) {
        var expectedUserLoginsWithPrefix = Arrays.stream(userLoginsWithoutPrefix).map(login -> testPrefix + login).toArray(String[]::new);
        var conversationMembers = getParticipants(channelId).stream().map(ConversationParticipant::getUser);
        assertThat(conversationMembers).extracting(User::getLogin).doesNotContain(expectedUserLoginsWithPrefix);
    }

    void removeUsersFromConversation(Long conversationId, String... userLoginsWithoutPrefix) {
        for (String login : userLoginsWithoutPrefix) {
            var user = userUtilService.getUserByLogin(testPrefix + login);
            var participant = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(conversationId, user.getId());
            participant.ifPresent(conversationParticipant -> conversationParticipantRepository.delete(conversationParticipant));
        }
    }

    void setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration courseInformationSharingConfiguration) {
        var persistedCourse = courseRepository.findByIdElseThrow(exampleCourseId);
        persistedCourse.setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);
        persistedCourse = courseRepository.saveAndFlush(persistedCourse);
        assertThat(persistedCourse.getCourseInformationSharingConfiguration()).isEqualTo(courseInformationSharingConfiguration);
    }

    ChannelDTO createChannel(boolean isPublicChannel, String name) throws Exception {
        var channelDTO = new ChannelDTO();
        channelDTO.setName(name);
        channelDTO.setIsPublic(isPublicChannel);
        channelDTO.setIsAnnouncementChannel(false);
        channelDTO.setDescription("general channel");

        var chat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/channels", channelDTO, ChannelDTO.class, HttpStatus.CREATED);
        resetWebsocketMock();
        return chat;
    }

    ChannelDTO createCourseWideChannel(String name) throws Exception {
        var channelDTO = new ChannelDTO();
        channelDTO.setName(name);
        channelDTO.setIsPublic(true);
        channelDTO.setIsCourseWide(true);
        channelDTO.setIsAnnouncementChannel(false);
        channelDTO.setDescription("course wide channel");

        var chat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/channels", channelDTO, ChannelDTO.class, HttpStatus.CREATED);
        resetWebsocketMock();
        return chat;
    }

    GroupChatDTO createGroupChat(String... userLoginsWithoutPrefix) throws Exception {
        var loginsWithPrefix = Arrays.stream(userLoginsWithoutPrefix).map(login -> testPrefix + login).toArray(String[]::new);
        var chat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/group-chats", Arrays.stream(loginsWithPrefix).toList(), GroupChatDTO.class,
                HttpStatus.CREATED);
        this.resetWebsocketMock();
        return chat;
    }

    void addUsersToConversation(Long conversationId, String... userLoginsWithoutPrefix) {
        for (String login : userLoginsWithoutPrefix) {
            var user = userUtilService.getUserByLogin(testPrefix + login);
            var existing = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(conversationId, user.getId());
            if (existing.isPresent()) {
                continue;
            }
            var participant = new ConversationParticipant();
            participant.setConversation(conversationRepository.findByIdElseThrow(conversationId));
            participant.setIsModerator(false);
            participant.setUser(user);
            conversationParticipantRepository.save(participant);
        }
    }

    void hideConversation(Long conversationId, String userLoginWithoutPrefix) {
        var user = userUtilService.getUserByLogin(testPrefix + userLoginWithoutPrefix);
        var participant = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(conversationId, user.getId());
        participant.ifPresent(conversationParticipant -> {
            conversationParticipant.setIsHidden(true);
            conversationParticipantRepository.save(conversationParticipant);
        });
    }

    void favoriteConversation(Long conversationId, String userLoginWithoutPrefix) {
        var user = userUtilService.getUserByLogin(testPrefix + userLoginWithoutPrefix);
        var participant = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(conversationId, user.getId());
        participant.ifPresent(conversationParticipant -> {
            conversationParticipant.setIsFavorite(true);
            conversationParticipantRepository.save(conversationParticipant);
        });
    }

    void revokeChannelModeratorRole(Long channelId, String userLoginWithoutPrefix) {
        var user = userRepository.findOneByLogin(testPrefix + userLoginWithoutPrefix).orElseThrow();
        var participant = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channelId, user.getId()).orElseThrow();
        participant.setIsModerator(false);
        conversationParticipantRepository.save(participant);
    }

    void grantChannelModeratorRole(Long channelId, String userLoginWithoutPrefix) {
        var user = userRepository.findOneByLogin(testPrefix + userLoginWithoutPrefix).orElseThrow();
        var participant = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channelId, user.getId()).orElseThrow();
        participant.setIsModerator(true);
        conversationParticipantRepository.save(participant);
    }

    void archiveChannel(Long channelId) {
        var dbChannel = channelRepository.findById(channelId).orElseThrow();
        dbChannel.setIsArchived(true);
        channelRepository.save(dbChannel);
    }

    void unArchiveChannel(Long channelId) {
        var dbChannel = channelRepository.findById(channelId).orElseThrow();
        dbChannel.setIsArchived(false);
        channelRepository.save(dbChannel);
    }

    void assertUsersAreChannelModerators(Long channelId, String... userLoginsWithoutPrefix) {
        var loginsWithPrefix = Arrays.stream(userLoginsWithoutPrefix).map(login -> testPrefix + login).toArray(String[]::new);
        var channelModerators = getParticipants(channelId).stream().filter(ConversationParticipant::getIsModerator).map(ConversationParticipant::getUser);
        assertThat(channelModerators).extracting(User::getLogin).contains(loginsWithPrefix);
    }

    void assertUserAreNotChannelModerators(Long channelId, String... userLoginsWithoutPrefix) {
        var loginsWithPrefix = Arrays.stream(userLoginsWithoutPrefix).map(login -> testPrefix + login).toArray(String[]::new);
        var channelModerators = getParticipants(channelId).stream().filter(ConversationParticipant::getIsModerator).map(ConversationParticipant::getUser);
        assertThat(channelModerators).extracting(User::getLogin).doesNotContain(loginsWithPrefix);
    }

    void addUserAsChannelModerators(ChannelDTO channel, String loginWithoutPrefix) {
        var newModerator = userRepository.findOneByLogin(testPrefix + loginWithoutPrefix).orElseThrow();
        var moderatorParticipant = new ConversationParticipant();
        moderatorParticipant.setIsModerator(true);
        moderatorParticipant.setUser(newModerator);
        moderatorParticipant.setConversation(this.channelRepository.findById(channel.getId()).orElseThrow());
        conversationParticipantRepository.save(moderatorParticipant);
    }

    void resetWebsocketMock() {
        reset(this.websocketMessagingService);
    }

}
