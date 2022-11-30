package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.OneToOneChatDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.ConversationWebsocketDTO;
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
        verify(this.messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(ConversationWebsocketDTO.class));
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
        verify(this.messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(ConversationWebsocketDTO.class));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void postInOneToOneChat_firstPost_chatPartnerShouldBeNotifiedAboutNewConversation() throws Exception {
        var student1 = database.getUserByLogin("student1");
        var student2 = database.getUserByLogin("student2");
        // when
        var chat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats/", List.of("student2"), OneToOneChatDTO.class, HttpStatus.CREATED);
        this.postInConversation(chat.getId(), "student1");
        // then
        // both conversation participants should be notified that the conversation has been "created" by the first message being posted
        var topic1 = ConversationService.getConversationParticipantTopicName(exampleCourseId) + student1.getId();
        var topic2 = ConversationService.getConversationParticipantTopicName(exampleCourseId) + student2.getId();
        verify(messagingTemplate).convertAndSendToUser(eq("student1"), eq(topic1),
                argThat((argument) -> argument instanceof ConversationWebsocketDTO && ((ConversationWebsocketDTO) argument).getCrudAction().equals(MetisCrudAction.CREATE)
                        && ((ConversationWebsocketDTO) argument).getConversation().getId().equals(chat.getId())));
        verify(messagingTemplate).convertAndSendToUser(eq("student2"), eq(topic2),
                argThat((argument) -> argument instanceof ConversationWebsocketDTO && ((ConversationWebsocketDTO) argument).getCrudAction().equals(MetisCrudAction.CREATE)
                        && ((ConversationWebsocketDTO) argument).getConversation().getId().equals(chat.getId())));
    }

}
