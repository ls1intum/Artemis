package de.tum.in.www1.artemis.service.notifications;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants;
import de.tum.in.www1.artemis.repository.SingleUserNotificationRepository;
import de.tum.in.www1.artemis.service.MailService;

public class SingleUserNotificationServiceTest {

    @Autowired
    private static SingleUserNotificationService singleUserNotificationService;

    @Captor
    private static ArgumentCaptor<Notification> notificationCaptor;

    private Notification capturedNotification;

    @Mock
    private static User user;

    @Mock
    private static Exercise exercise;

    @Mock
    private static SingleUserNotificationRepository singleUserNotificationRepository;

    @Mock
    private static SimpMessageSendingOperations messagingTemplate;

    @Mock
    private static MailService mailService;

    @Mock
    private static NotificationSettingsService notificationSettingsService;

    @Mock
    private static Post post;

    @Mock
    private static Lecture lecture;

    @Mock
    private static Course course;

    private final static Long COURSE_ID = 27L;

    /**
     * Sets up all needed mocks and their wanted behavior once for all test cases.
     * These are the common mocks and structures which behavior is fixed and will not change
     */
    @BeforeAll
    public static void setUp() {
        mailService = mock(MailService.class);
        doNothing().when(mailService).sendNotificationEmailForMultipleUsers(any(), any(), any());

        course = mock(Course.class);
        when(course.getId()).thenReturn(COURSE_ID);

        notificationCaptor = ArgumentCaptor.forClass(Notification.class);

        singleUserNotificationRepository = mock(SingleUserNotificationRepository.class);

        messagingTemplate = mock(SimpMessageSendingOperations.class);

        notificationSettingsService = mock(NotificationSettingsService.class);

        singleUserNotificationService = spy(new SingleUserNotificationService(singleUserNotificationRepository, messagingTemplate, mailService, notificationSettingsService));

        exercise = mock(Exercise.class);

        user = mock(User.class);

        lecture = mock(Lecture.class);
        when(lecture.getCourse()).thenReturn(course);

        post = mock(Post.class);
        when(post.getExercise()).thenReturn(exercise);
        when(post.getLecture()).thenReturn(lecture);
        when(post.getAuthor()).thenReturn(user);
        when(post.getCourse()).thenReturn(course);
    }

    /**
     * Prepares and cleans the mocks that are modified during the tests
     */
    @BeforeEach
    public void cleanMocks() {
        reset(exercise);
        when(exercise.getCourseViaExerciseGroupOrCourseMember()).thenReturn(course);

        reset(singleUserNotificationService);

        reset(notificationSettingsService);

        reset(singleUserNotificationRepository);
        when(singleUserNotificationRepository.save(any())).thenReturn(null);

        reset(messagingTemplate);
    }

    /**
     * Auxiliary method that checks if the groupNotificationRepository was called once successfully with the correct notification (type)
     * @param expectedNotificationTitle is the title (NotificationTitleTypeConstants) of the expected notification
     */
    private void verifyRepositoryCallWithCorrectNotification(String expectedNotificationTitle) {
        verify(singleUserNotificationRepository, times(1)).save(notificationCaptor.capture());
        capturedNotification = notificationCaptor.getValue();
        assertThat(capturedNotification.getTitle()).isEqualTo(expectedNotificationTitle);
    }

    /// General notify Tests

    /**
     * Test for notifyStudentGroupAboutAttachmentChange method
     */
    @Test
    public void testNotifyUserAboutNewAnswerForExercise() {
        singleUserNotificationService.notifyUserAboutNewAnswerForExercise(post, course);
        verifyRepositoryCallWithCorrectNotification(NotificationTitleTypeConstants.NEW_REPLY_FOR_EXERCISE_POST_TITLE);
    }

    /**
     * Test for notifyUserAboutNewAnswerForLecture method
     */
    @Test
    public void testNotifyUserAboutNewAnswerForLecture() {
        singleUserNotificationService.notifyUserAboutNewAnswerForLecture(post, course);
        verifyRepositoryCallWithCorrectNotification(NotificationTitleTypeConstants.NEW_REPLY_FOR_LECTURE_POST_TITLE);
    }

    /**
     * Test for notifyUserAboutNewAnswerForCoursePost method
     */
    @Test
    public void testNotifyUserAboutNewAnswerForCoursePost() {
        singleUserNotificationService.notifyUserAboutNewAnswerForCoursePost(post, course);
        verifyRepositoryCallWithCorrectNotification(NotificationTitleTypeConstants.NEW_REPLY_FOR_COURSE_POST_TITLE);
    }

    /// Save & Send related Tests

    /**
     * Test for saveAndSend method for an exam exercise update (notification)
     * Checks if the notification target contains the problem statement for transmitting it to the user via WebSocket
     */
    @Test
    public void testSaveAndSend_CourseRelatedNotifications() {
        when(notificationSettingsService.checkNotificationTypeForEmailSupport(any())).thenReturn(true);
        when(notificationSettingsService.checkIfNotificationEmailIsAllowedBySettingsForGivenUser(any(), any())).thenReturn(true);

        singleUserNotificationService.notifyUserAboutNewAnswerForCoursePost(post, course);

        // inside public saveAndSend method
        verify(singleUserNotificationRepository, times(1)).save(any());
        verify(messagingTemplate, times(1)).convertAndSend(any(), (Notification) any());

        // inside private prepareSingleUserNotificationEmail method
        verify(notificationSettingsService, times(1)).checkNotificationTypeForEmailSupport(any());
        verify(notificationSettingsService, times(1)).checkIfNotificationEmailIsAllowedBySettingsForGivenUser(any(), any());
        verify(mailService, times(1)).sendNotificationEmail(any(), any(), any());
    }
}
