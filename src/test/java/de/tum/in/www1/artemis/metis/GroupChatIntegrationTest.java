package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.GroupChatDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.ConversationWebsocketDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;

class GroupChatIntegrationTest extends AbstractConversationTest {

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void startGroupChat_asStudent1WithStudent2AndStudent3_shouldCreateGroupChat() throws Exception {
        // when
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // then
        assertThat(chat).isNotNull();
        assertParticipants(chat.getId(), 3, "student1", "student2", "student3");
        // members of the created group chat are only notified in case the first message within the conversation is created
        verify(this.messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(ConversationWebsocketDTO.class));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void startGroupChat_invalidNumberOfChatPartners_shouldReturnBadRequest() throws Exception {
        // chat with too many users
        // then
        var loginList = new ArrayList<String>();
        for (int i = 2; i <= 11; i++) {
            loginList.add("student" + i);
        }
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/group-chats/", loginList, GroupChatDTO.class, HttpStatus.BAD_REQUEST);
        // chat with too few users
        // then
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/group-chats/", List.of(), GroupChatDTO.class, HttpStatus.BAD_REQUEST);

    }

    @Test
    @WithMockUser(username = "student42", roles = "USER")
    void startGroupChat_notAllowedAsNotStudentInCourse_shouldReturnBadRequest() throws Exception {
        // then
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/group-chats/", List.of("student2", "student3"), GroupChatDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void postInGroupChat_firstPost_chatPartnerShouldBeNotifiedAboutNewConversation() throws Exception {
        // given
        var student1 = database.getUserByLogin("student1");
        var student2 = database.getUserByLogin("student2");
        var student3 = database.getUserByLogin("student3");
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // when
        this.postInConversation(chat.getId(), "student1");
        // then
        // all conversation participants should be notified that the conversation has been "created" by the first message being posted
        var topic1 = ConversationService.getConversationParticipantTopicName(exampleCourseId) + student1.getId();
        var topic2 = ConversationService.getConversationParticipantTopicName(exampleCourseId) + student2.getId();
        var topic3 = ConversationService.getConversationParticipantTopicName(exampleCourseId) + student3.getId();
        verify(messagingTemplate).convertAndSendToUser(eq("student1"), eq(topic1),
                argThat((argument) -> argument instanceof ConversationWebsocketDTO && ((ConversationWebsocketDTO) argument).getCrudAction().equals(MetisCrudAction.CREATE)
                        && ((ConversationWebsocketDTO) argument).getConversation().getId().equals(chat.getId())));
        verify(messagingTemplate).convertAndSendToUser(eq("student2"), eq(topic2),
                argThat((argument) -> argument instanceof ConversationWebsocketDTO && ((ConversationWebsocketDTO) argument).getCrudAction().equals(MetisCrudAction.CREATE)
                        && ((ConversationWebsocketDTO) argument).getConversation().getId().equals(chat.getId())));
        verify(messagingTemplate).convertAndSendToUser(eq("student3"), eq(topic3),
                argThat((argument) -> argument instanceof ConversationWebsocketDTO && ((ConversationWebsocketDTO) argument).getCrudAction().equals(MetisCrudAction.CREATE)
                        && ((ConversationWebsocketDTO) argument).getConversation().getId().equals(chat.getId())));

    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void updateGroupChat_updateName_shouldUpdateName() throws Exception {
        // given
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // when
        chat.setName("updated");
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/group-chats/" + chat.getId(), chat, GroupChatDTO.class, HttpStatus.OK);
        // then
        var updatedGroupChat = groupChatRepository.findById(chat.getId()).get();
        assertParticipants(updatedGroupChat.getId(), 3, "student1", "student2", "student3");
        assertThat(updatedGroupChat.getName()).isEqualTo("updated");

        // ToDO: add verify of websocket

    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void updateGroupChat_notAMemberOfGroupChat_shouldReturnForbidden() throws Exception {
        // given
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // when
        chat.setName("updated");
        database.changeUser("student42");
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/group-chats/" + chat.getId(), chat, GroupChatDTO.class, HttpStatus.FORBIDDEN);
        // then
        var groupChat = groupChatRepository.findById(chat.getId()).get();
        assertParticipants(groupChat.getId(), 3, "student1", "student2", "student3");
        assertThat(groupChat.getName()).isNotEqualTo("updated");

        // ToDO: add verify of websocket

    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void registerUsersToGroupChat_memberOfGroupChat_shouldAddUsers() throws Exception {
        // given
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // when
        // Note: adding student1 and student2 again should not be a problem (silently ignored)
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/register", List.of("student1", "student2", "student4", "student5"),
                HttpStatus.OK);
        // then
        assertParticipants(chat.getId(), 5, "student1", "student2", "student3", "student4", "student5");

        // ToDO: add verify of websocket

    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void registerUsersToGroupChat_asNonMember_shouldReturnForbidden() throws Exception {
        // given
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // when
        database.changeUser("student42");
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/register", List.of("student1", "student2", "student4", "student5"),
                HttpStatus.FORBIDDEN);

        // then
        assertParticipants(chat.getId(), 3, "student1", "student2", "student3");

        // ToDO: add verify of websocket

    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void registerUsersToGroupChat_wouldBreakGroupChatLimit_shouldReturnBadRequest() throws Exception {
        // given
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // when
        var loginList = new ArrayList<String>();
        for (int i = 4; i <= 15; i++) {
            loginList.add("student" + i);
        }
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/register", loginList, HttpStatus.BAD_REQUEST);
        assertParticipants(chat.getId(), 3, "student1", "student2", "student3");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void deregisterUsersFromGroupChat_asMember_shouldRemoveMembers() throws Exception {
        // given
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // when
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/deregister", List.of("student2"), HttpStatus.OK);
        // then
        assertParticipants(chat.getId(), 2, "student1", "student3");

        // ToDO: add verify of websocket

    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void deregisterUsersFromGroupChat_selfDeregistration_shouldRequestingMember() throws Exception {
        // given
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // when
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/deregister", List.of("student1"), HttpStatus.OK);
        // then
        assertParticipants(chat.getId(), 2, "student2", "student3");

        // ToDO: add verify of websocket

    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void deregisterUsersFromGroupChat_asNonMember_shouldReturnForbidden() throws Exception {
        // given
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // when
        database.changeUser("student42");
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/deregister", List.of("student2"), HttpStatus.FORBIDDEN);
        // then
        assertParticipants(chat.getId(), 3, "student1", "student2", "student3");

        // ToDO: add verify of websocket
    }

    private GroupChatDTO createGroupChatWithStudent1To3() throws Exception {
        return request.postWithResponseBody("/api/courses/" + exampleCourseId + "/group-chats/", List.of("student2", "student3"), GroupChatDTO.class, HttpStatus.CREATED);
    }

    // ToDo: ich glaube beim group chat es waere es besser wie beim channel das nutzer gleich benachrichtigt werden wenn sie teilnehmen!! --> AENDERN

}
