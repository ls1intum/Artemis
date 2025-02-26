package de.tum.cit.aet.artemis.communication.notifications.service;

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

import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.domain.conversation.GroupChat;
import de.tum.cit.aet.artemis.communication.domain.conversation.OneToOneChat;
import de.tum.cit.aet.artemis.communication.domain.notification.ConversationNotification;
import de.tum.cit.aet.artemis.communication.domain.notification.Notification;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ConversationNotificationRepository;
import de.tum.cit.aet.artemis.communication.service.notifications.ConversationNotificationService;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationParticipantTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.NotificationTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ConversationNotificationServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "conversationnotificationservice";

    @Autowired
    private ConversationNotificationRepository conversationNotificationRepository;

    @Autowired
    private ConversationNotificationService conversationNotificationService;

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    @Autowired
    private ConversationTestRepository conversationRepository;

    @Autowired
    private ConversationParticipantTestRepository conversationParticipantRepository;

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private NotificationTestRepository notificationTestRepository;

    private OneToOneChat oneToOneChat;

    private GroupChat groupChat;

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

        groupChat = new GroupChat();
        groupChat.setCourse(course);
        groupChat.setCreator(user1);
        groupChat.setCreationDate(ZonedDateTime.now());
        groupChat.setConversationParticipants(Set.of(conversationParticipant1, conversationParticipant2));
        groupChat = conversationRepository.save(groupChat);

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
        Post post = createAndSavePostForUser(user1, oneToOneChat);

        ConversationNotification notification = conversationNotificationService.createNotification(post, oneToOneChat, course, Set.of());
        conversationNotificationService.notifyAboutNewMessage(post, notification, Set.of(user2));
        verifyRepositoryCallWithCorrectNotification(NEW_MESSAGE_TITLE);

        Notification sentNotification = notificationTestRepository.findAll().stream().max(Comparator.comparing(DomainObject::getId)).orElseThrow();

        verify(generalInstantNotificationService).sendNotification(sentNotification, Set.of(user2), post);

        var participants = conversationParticipantRepository.findConversationParticipantsByConversationId(oneToOneChat.getId());
        // make sure that objects can be deleted after notification is saved
        conversationMessageRepository.deleteAllById(List.of(post.getId()));
        conversationParticipantRepository.deleteAllById(participants.stream().map(ConversationParticipant::getId).toList());
        conversationRepository.deleteAllById(List.of(oneToOneChat.getId()));
    }

    // This caused a bug in notifications on the mobile apps, see https://github.com/ls1intum/artemis-android/issues/391
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createNotificationForNewMessageInGroupChatContainsNonNullGroupName() {
        Post post = createAndSavePostForUser(user1, groupChat);

        ConversationNotification notification = conversationNotificationService.createNotification(post, groupChat, course, Set.of());
        String[] notificationPlaceholders = notification.getTransientPlaceholderValuesAsArray();
        String conversationName = notificationPlaceholders[3];
        assertThat(conversationName).isNotNull();
    }

    private Post createAndSavePostForUser(User user, Conversation conversation) {
        Post post = new Post();
        post.setAuthor(user);
        post.setCreationDate(ZonedDateTime.now());
        post.setConversation(conversation);
        post.setVisibleForStudents(true);
        post.setContent("hi test");
        return conversationMessageRepository.save(post);
    }
}
