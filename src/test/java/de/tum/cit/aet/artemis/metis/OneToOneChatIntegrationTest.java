package de.tum.cit.aet.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.dto.MetisCrudAction;
import de.tum.cit.aet.artemis.communication.dto.OneToOneChatDTO;
import de.tum.cit.aet.artemis.communication.dto.PostDTO;
import de.tum.cit.aet.artemis.core.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.user.UserFactory;

class OneToOneChatIntegrationTest extends AbstractConversationTest {

    private static final String TEST_PREFIX = "ootest";

    @BeforeEach
    @Override
    void setupTestScenario() throws Exception {
        super.setupTestScenario();
        userUtilService.addUsers(TEST_PREFIX, 3, 0, 0, 0);
        if (userRepository.findOneByLogin(testPrefix + "student42").isEmpty()) {
            userRepository.save(UserFactory.generateActivatedUser(testPrefix + "student42"));
        }
    }

    @AfterEach
    void tearDown() {
        conversationMessageRepository.deleteAll();
        conversationRepository.deleteAll();
    }

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void startOneToOneChat_asStudent1_shouldCreateMultipleOneToOneChats() throws Exception {
        // when
        var chat1 = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student2"), OneToOneChatDTO.class,
                HttpStatus.CREATED);

        var chat2 = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student3"), OneToOneChatDTO.class,
                HttpStatus.CREATED);
        // then
        assertThat(chat1).isNotNull();
        assertParticipants(chat1.getId(), 2, "student1", "student2");
        assertThat(chat2).isNotNull();
        assertParticipants(chat2.getId(), 2, "student1", "student3");
        // members of the created one to one chat are only notified in case the first message within the conversation is created
        verifyNoParticipantTopicWebsocketSent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void startOneToOneChat_asStudent2WithStudent1_shouldUseExistingOneToOneChat() throws Exception {
        var chat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student2"), OneToOneChatDTO.class,
                HttpStatus.CREATED);

        userUtilService.changeUser(TEST_PREFIX + "student2");
        var sameChat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student1"), OneToOneChatDTO.class,
                HttpStatus.CREATED);

        assertThat(chat.getId()).isNotNull();
        assertThat(chat.getId()).isEqualTo(sameChat.getId());
        assertThat(chat.getCreator().getLogin()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(sameChat.getCreator().getLogin()).isEqualTo(TEST_PREFIX + "student2");
        assertParticipants(chat.getId(), 2, "student1", "student2");
        assertParticipants(sameChat.getId(), 2, "student1", "student2");

        // members of the created one to one chat are only notified in case the first message within the conversation is created
        verifyNoParticipantTopicWebsocketSent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void startOneToOneChat_invalidNumberOfChatPartners_shouldReturnBadRequest() throws Exception {
        // chat with too many users
        // then
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student2", testPrefix + "student3"), OneToOneChatDTO.class,
                HttpStatus.BAD_REQUEST);
        // chat with too few users
        // then
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats", List.of(), OneToOneChatDTO.class, HttpStatus.BAD_REQUEST);
        verifyNoParticipantTopicWebsocketSent();

    }

    @ParameterizedTest
    @EnumSource(value = CourseInformationSharingConfiguration.class, names = { "COMMUNICATION_ONLY", "DISABLED" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void startOneToOneChat_messagingFeatureDeactivated_shouldReturnForbidden(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        startOneToOneChat_messagingDeactivated(courseInformationSharingConfiguration);

    }

    void startOneToOneChat_messagingDeactivated(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);

        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student2"), OneToOneChatDTO.class, HttpStatus.FORBIDDEN);

        // active messaging again
        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void startOneToOneChat_notAllowedAsNotStudentInCourse_shouldReturnBadRequest() throws Exception {
        // then
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student2"), OneToOneChatDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void startOneToOneChat_chatAlreadyExists_shouldReturnExistingChat() throws Exception {
        // when
        var chat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student2"), OneToOneChatDTO.class,
                HttpStatus.CREATED);
        var chat2 = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student2"), OneToOneChatDTO.class,
                HttpStatus.CREATED);
        // then
        assertThat(chat).isNotNull();
        assertThat(chat2).isNotNull();
        assertThat(chat.getId()).isEqualTo(chat2.getId());
        assertParticipants(chat.getId(), 2, "student1", "student2");

        // members of the created one to one chat are only notified in case the first message within the conversation is created
        verifyNoParticipantTopicWebsocketSent();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void postInOneToOneChat_firstPost_chatPartnerShouldBeNotifiedAboutNewConversation() throws Exception {
        // when
        var chat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student2"), OneToOneChatDTO.class,
                HttpStatus.CREATED);
        var post = this.postInConversation(chat.getId(), "student1");
        // then
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.CREATE, chat.getId(), "student1", "student2");
        verify(websocketMessagingService, timeout(2000).times(2)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostDTO postDTO && postDTO.post().equals(post)));
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.CREATE, MetisCrudAction.NEW_MESSAGE);

    }
}
