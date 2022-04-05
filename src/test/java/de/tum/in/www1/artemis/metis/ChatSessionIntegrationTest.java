package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.metis.ChatSession;
import de.tum.in.www1.artemis.domain.metis.UserChatSession;
import de.tum.in.www1.artemis.repository.metis.ChatSessionRepository;
import de.tum.in.www1.artemis.service.metis.ChatService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.web.websocket.dto.metis.ChatSessionDTO;

class ChatSessionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatService chatService;

    private ChatSession existingChatSession;

    private Course course;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(5, 5, 0, 1);

        course = database.createCourse(1L);

        existingChatSession = database.createChatSession(course);

        SimpMessageSendingOperations messagingTemplate = mock(SimpMessageSendingOperations.class);
        doNothing().when(messagingTemplate).convertAndSend(any());
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    // ChatSession

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateChatSession() throws Exception {
        ChatSession chatSessionToSave = createChatSession(course, database);

        ChatSession createdChatSession = request.postWithResponseBody("/api/courses/" + course.getId() + "/chatSessions/", chatSessionToSave, ChatSession.class,
                HttpStatus.CREATED);

        checkCreatedUserChatSessions(createdChatSession.getUserChatSessions(), createdChatSession.getCreationDate());
        checkCreatedChatSession(chatSessionToSave, createdChatSession);

        assertThat(chatSessionRepository.findById(createdChatSession.getId())).isNotEmpty();

        // checks if members of the created chat session were notified via broadcast
        verify(messagingTemplate, times(2)).convertAndSend(anyString(), any(ChatSessionDTO.class));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateChatSession_badRequest() throws Exception {
        ChatSession chatSessionToSave = new ChatSession();

        // chatSession without required userChatSession
        createChatSessionBadRequest(chatSessionToSave);

        chatSessionToSave = createChatSession(course, database);
        chatSessionToSave.setId(1L);

        // chatSession with existing ID
        createChatSessionBadRequest(chatSessionToSave);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetUserChatSessionsByUserId() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        List<ChatSession> chatSessionsOfUser;

        chatSessionsOfUser = request.getList("/api/courses/" + course.getId() + "/chatSessions/", HttpStatus.OK, ChatSession.class, params);
        assertThat(chatSessionsOfUser.get(0)).isEqualTo(existingChatSession);

        database.changeUser("student2");
        chatSessionsOfUser = request.getList("/api/courses/" + course.getId() + "/chatSessions/", HttpStatus.OK, ChatSession.class, params);
        assertThat(chatSessionsOfUser.get(0)).isEqualTo(existingChatSession);

        database.changeUser("student3");
        chatSessionsOfUser = request.getList("/api/courses/" + course.getId() + "/chatSessions/", HttpStatus.OK, ChatSession.class, params);
        assertThat(chatSessionsOfUser).isEmpty();
    }

    @Test
    void testGetChatSessionById() {
        ChatSession chatSession = chatService.getChatSessionById(existingChatSession.getId());
        assertThat(chatSession).isEqualTo(existingChatSession);
    }

    private void createChatSessionBadRequest(ChatSession chatSessionToSave) throws Exception {
        ChatSession createdChatSession = request.postWithResponseBody("/api/courses/" + course.getId() + "/chatSessions/", chatSessionToSave, ChatSession.class,
                HttpStatus.BAD_REQUEST);
        assertThat(createdChatSession).isNull();

        // checks if members of the created chat session were not notified via broadcast
        verify(messagingTemplate, times(0)).convertAndSend(anyString(), any(ChatSessionDTO.class));
    }

    static ChatSession createChatSession(Course course, DatabaseUtilService databaseUtilService) {
        ChatSession chatSession = new ChatSession();

        UserChatSession userChatSession2 = new UserChatSession();
        userChatSession2.setUser(databaseUtilService.getUserByLogin("student2"));
        userChatSession2.setLastRead(chatSession.getCreationDate());

        chatSession.getUserChatSessions().add(userChatSession2);
        chatSession.setCourse(course);

        return chatSession;
    }

    private void checkCreatedChatSession(ChatSession expectedChatSession, ChatSession createdChatSession) {
        assertThat(createdChatSession).isNotNull();
        assertThat(createdChatSession.getId()).isNotNull();
        assertThat(createdChatSession.getCreationDate()).isNotNull();
        assertThat(createdChatSession.getLastMessageDate()).isNotNull();

        assertThat(createdChatSession.getCourse()).isEqualTo(expectedChatSession.getCourse());
        assertThat(createdChatSession.getCreationDate()).isEqualTo(createdChatSession.getLastMessageDate());
    }

    private void checkCreatedUserChatSessions(Set<UserChatSession> userChatSessions, ZonedDateTime creationDate) {
        // check each individual user chat session
        userChatSessions.forEach(userChatSession -> {
            assertThat(userChatSession.isClosed()).isFalse();
            assertThat(userChatSession.getUser()).isNotNull();
            assertThat(userChatSession.getLastRead()).isEqualTo(creationDate);
        });
    }
}
