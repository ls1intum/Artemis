package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.metis.Conversation;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.ConversationType;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.repository.metis.ConversationRepository;
import de.tum.in.www1.artemis.web.websocket.dto.metis.ConversationDTO;

class ConversationIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    private Conversation existingConversation;

    private Course course;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(5, 5, 0, 1);
        course = database.createCourse(1L);
        existingConversation = database.createDirectConversation(course);
        doNothing().when(messagingTemplate).convertAndSendToUser(any(), any(), any());
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    // Conversation

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateConversation() throws Exception {
        Conversation conversationToSave = directConversationToCreate(course, database.getUserByLogin("student2"));

        Conversation createdConversation = request.postWithResponseBody("/api/courses/" + course.getId() + "/conversations/", conversationToSave, Conversation.class,
                HttpStatus.CREATED);

        checkCreatedConversationParticipants(createdConversation.getConversationParticipants());
        checkCreatedDirerctConversation(createdConversation);

        assertThat(conversationRepository.findById(createdConversation.getId())).isNotEmpty();

        // members of the created conversation are only notified in case the first message within the conversation is created
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(ConversationDTO.class));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateConversation_badRequest() throws Exception {
        Conversation conversationToSave = new Conversation();
        conversationToSave.setType(ConversationType.DIRECT);

        // conversation without required conversationParticipant
        createConversationBadRequest(conversationToSave);

        conversationToSave = directConversationToCreate(course, database.getUserByLogin("student2"));
        conversationToSave.setId(1L);

        // conversation with existing ID
        createConversationBadRequest(conversationToSave);

        // conversation with user's own conversationParticipant object
        conversationToSave = directConversationToCreate(course, database.getUserByLogin("student2"));
        ConversationParticipant conversationParticipant = new ConversationParticipant();
        conversationParticipant.setUser(database.getUserByLogin("student1"));
        conversationToSave.getConversationParticipants().add(conversationParticipant);
        createConversationBadRequest(conversationToSave);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "USER")
    void testGetActiveConversationsOfCurrentUser() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        List<Conversation> conversationsOfUser;

        // we need to create a post within the conversation so that it is active and returned by the conversation service
        Post post = new Post();
        post.setAuthor(database.getUserByLogin("tutor1"));
        post.setDisplayPriority(DisplayPriority.NONE);
        post.setConversation(existingConversation);

        request.postWithResponseBody("/api/courses/" + course.getId() + "/messages", post, Post.class, HttpStatus.CREATED);

        conversationsOfUser = request.getList("/api/courses/" + course.getId() + "/conversations/", HttpStatus.OK, Conversation.class, params);
        assertThat(conversationsOfUser.get(0)).isEqualTo(existingConversation);

        database.changeUser("tutor2");
        conversationsOfUser = request.getList("/api/courses/" + course.getId() + "/conversations/", HttpStatus.OK, Conversation.class, params);
        assertThat(conversationsOfUser.get(0)).isEqualTo(existingConversation);

        database.changeUser("tutor3");
        conversationsOfUser = request.getList("/api/courses/" + course.getId() + "/conversations/", HttpStatus.OK, Conversation.class, params);
        assertThat(conversationsOfUser).isEmpty();
    }

    private void createConversationBadRequest(Conversation conversationToSave) throws Exception {
        Conversation createdConversation = request.postWithResponseBody("/api/courses/" + course.getId() + "/conversations/", conversationToSave, Conversation.class,
                HttpStatus.BAD_REQUEST);
        assertThat(createdConversation).isNull();

        // checks if members of the created conversation were not notified via broadcast
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(ConversationDTO.class));
    }

    static Conversation directConversationToCreate(Course course, User conversatingUser) {
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.DIRECT);

        ConversationParticipant conversationParticipant2 = new ConversationParticipant();
        conversationParticipant2.setUser(conversatingUser);
        conversationParticipant2.setLastRead(conversation.getCreationDate());

        conversation.getConversationParticipants().add(conversationParticipant2);
        conversation.setCourse(course);

        return conversation;
    }

    private void checkCreatedDirerctConversation(Conversation createdConversation) {
        assertThat(createdConversation).isNotNull();
        assertThat(createdConversation.getId()).isNotNull();
        assertThat(createdConversation.getCreationDate()).isNotNull();
        assertThat(createdConversation.getLastMessageDate()).isNull();
        assertThat(createdConversation.getType()).isEqualTo(ConversationType.DIRECT);
        assertThat(createdConversation.getName()).isNull(); // Null for direct conversations
    }

    private void checkCreatedConversationParticipants(Set<ConversationParticipant> conversationParticipants) {
        // check each individual conversationParticipant
        conversationParticipants.forEach(conversationParticipant -> {
            assertThat(conversationParticipant.getUser()).isNotNull();
        });
    }
}
