package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.notification.NotificationConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.OneToOneChat;
import de.tum.in.www1.artemis.domain.notification.ConversationNotification;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationMessageRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationNotificationRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationRepository;

class ConversationNotificationServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "conversationnotificationservice";

    @Autowired
    private ConversationNotificationRepository conversationNotificationRepository;

    @Autowired
    private ConversationNotificationService conversationNotificationService;

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationParticipantRepository conversationParticipantRepository;

    @Autowired
    UserRepository userRepository;

    private OneToOneChat oneToOneChat;

    private User user1;

    @BeforeEach
    void setUp() {
        this.database.addUsers(TEST_PREFIX, 5, 1, 0, 1);
        Course course = this.database.createCourse();
        user1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        User user2 = userRepository.findOneByLogin(TEST_PREFIX + "tutor1").get();

        oneToOneChat = new OneToOneChat();
        oneToOneChat.setCourse(course);
        oneToOneChat.setCreator(user1);
        oneToOneChat.setCreationDate(ZonedDateTime.now());
        ConversationParticipant conversationParticipant1 = new ConversationParticipant();
        conversationParticipant1.setUser(user1);
        ConversationParticipant conversationParticipant2 = new ConversationParticipant();
        conversationParticipant2.setUser(user2);
        conversationParticipantRepository.saveAll(Set.of(conversationParticipant1, conversationParticipant2));
        oneToOneChat.setConversationParticipants(Set.of(conversationParticipant1, conversationParticipant2));
        oneToOneChat = conversationRepository.save(oneToOneChat);

        conversationNotificationRepository.deleteAll();
    }

    private void verifyRepositoryCallWithCorrectNotification(String expectedNotificationTitle) {
        List<ConversationNotification> capturedNotifications = conversationNotificationRepository.findAll();
        Notification capturedNotification = capturedNotifications.get(0);
        assertThat(capturedNotification.getTitle()).isEqualTo(expectedNotificationTitle);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void notifyAboutNewMessageInConversation() {
        Post post = new Post();
        post.setAuthor(user1);
        post.setCreationDate(ZonedDateTime.now());
        post.setConversation(oneToOneChat);
        post.setVisibleForStudents(true);
        post.setContent("hi test");
        post = conversationMessageRepository.save(post);

        conversationNotificationService.notifyAboutNewMessage(post);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/conversation/" + post.getConversation().getId() + "/notifications"), (Object) any());
        verifyRepositoryCallWithCorrectNotification(NEW_MESSAGE_TITLE);

        // make sure that objects can be deleted after notification is saved
        conversationMessageRepository.deleteAll();
        conversationParticipantRepository.deleteAll();
        conversationRepository.deleteAll();
    }
}
