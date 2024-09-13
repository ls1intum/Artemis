package de.tum.cit.aet.artemis.service.notifications;

import static de.tum.cit.aet.artemis.communication.domain.notification.NotificationConstants.NEW_MESSAGE_TITLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.OneToOneChat;
import de.tum.cit.aet.artemis.communication.domain.notification.ConversationNotification;
import de.tum.cit.aet.artemis.communication.domain.notification.Notification;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.NotificationRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ConversationNotificationRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ConversationRepository;
import de.tum.cit.aet.artemis.communication.service.notifications.ConversationNotificationService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.course.CourseUtilService;
import de.tum.cit.aet.artemis.user.UserUtilService;

class ConversationNotificationServiceTest extends AbstractSpringIntegrationIndependentTest {

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
    private UserRepository userRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private NotificationRepository notificationRepository;

    private OneToOneChat oneToOneChat;

    private User user1;

    private User user2;

    private Course course;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        course = courseUtilService.createCourse();
        user1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        user2 = userRepository.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow();

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
        Notification capturedNotification = capturedNotifications.getFirst();
        assertThat(capturedNotification.getTitle()).isEqualTo(expectedNotificationTitle);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createNotificationForNewMessageInConversation() {
        Post post = new Post();
        post.setAuthor(user1);
        post.setCreationDate(ZonedDateTime.now());
        post.setConversation(oneToOneChat);
        post.setVisibleForStudents(true);
        post.setContent("hi test");
        post = conversationMessageRepository.save(post);

        ConversationNotification notification = conversationNotificationService.createNotification(post, oneToOneChat, course, Set.of());
        conversationNotificationService.notifyAboutNewMessage(post, notification, Set.of(user2));
        verifyRepositoryCallWithCorrectNotification(NEW_MESSAGE_TITLE);

        Notification sentNotification = notificationRepository.findAll().stream().max(Comparator.comparing(DomainObject::getId)).orElseThrow();

        verify(generalInstantNotificationService).sendNotification(sentNotification, Set.of(user2), post);

        var participants = conversationParticipantRepository.findConversationParticipantsByConversationId(oneToOneChat.getId());
        // make sure that objects can be deleted after notification is saved
        conversationMessageRepository.deleteAllById(List.of(post.getId()));
        conversationParticipantRepository.deleteAllById(participants.stream().map(ConversationParticipant::getId).toList());
        conversationRepository.deleteAllById(List.of(oneToOneChat.getId()));
    }
}
