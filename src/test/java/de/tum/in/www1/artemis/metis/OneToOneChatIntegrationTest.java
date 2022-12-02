package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.OneToOneChatDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;

class OneToOneChatIntegrationTest extends AbstractConversationTest {

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void startOneToOneChat_asStudent1WithStudent2_shouldCreateOneToOneChat() throws Exception {
        // when
        var chat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats/", List.of("student2"), OneToOneChatDTO.class, HttpStatus.CREATED);
        // then
        assertThat(chat).isNotNull();
        assertParticipants(chat.getId(), 2, "student1", "student2");
        // members of the created one to one chat are only notified in case the first message within the conversation is created
        verifyNoParticipantTopicWebsocketSent();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void startOneToOneChat_invalidNumberOfChatPartners_shouldReturnBadRequest() throws Exception {
        // chat with too many users
        // then
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats/", List.of("student4", "student3"), OneToOneChatDTO.class, HttpStatus.BAD_REQUEST);
        // chat with too few users
        // then
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats/", List.of(), OneToOneChatDTO.class, HttpStatus.BAD_REQUEST);
        verifyNoParticipantTopicWebsocketSent();

    }

    @Test
    @WithMockUser(username = "student42", roles = "USER")
    void startOneToOneChat_notAllowedAsNotStudentInCourse_shouldReturnBadRequest() throws Exception {
        // then
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats/", List.of("student2"), OneToOneChatDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void startOneToOneChat_chatAlreadyExists_shouldReturnExistingChat() throws Exception {
        // when
        var chat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats/", List.of("student2"), OneToOneChatDTO.class, HttpStatus.CREATED);
        var chat2 = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats/", List.of("student2"), OneToOneChatDTO.class, HttpStatus.CREATED);
        // then
        assertThat(chat).isNotNull();
        assertThat(chat2).isNotNull();
        assertThat(chat.getId()).isEqualTo(chat2.getId());
        assertParticipants(chat.getId(), 2, "student1", "student2");

        // members of the created one to one chat are only notified in case the first message within the conversation is created
        verifyNoParticipantTopicWebsocketSent();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void postInOneToOneChat_firstPost_chatPartnerShouldBeNotifiedAboutNewConversation() throws Exception {
        // when
        var chat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats/", List.of("student2"), OneToOneChatDTO.class, HttpStatus.CREATED);
        this.postInConversation(chat.getId(), "student1");
        // then
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.CREATE, chat.getId(), "student1", "student2");
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.NEW_MESSAGE, chat.getId(), "student1", "student2");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.CREATE, MetisCrudAction.NEW_MESSAGE);
    }

}
