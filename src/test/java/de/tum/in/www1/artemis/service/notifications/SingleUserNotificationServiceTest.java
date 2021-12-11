package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.repository.SingleUserNotificationRepository;
import de.tum.in.www1.artemis.service.MailService;

public class SingleUserNotificationServiceTest {

    private static SingleUserNotificationService singleUserNotificationService;

    @Captor
    private static ArgumentCaptor<Notification> notificationCaptor;

    private Notification capturedNotification;

    private static User user;

    private static Exercise exercise;

    private static FileUploadExercise fileUploadExercise;

    @Mock
    private static SingleUserNotificationRepository singleUserNotificationRepository;

    @Mock
    private static SimpMessageSendingOperations messagingTemplate;

    @Mock
    private static MailService mailService;

    @Mock
    private static NotificationSettingsService notificationSettingsService;

    private static Post post;

    private static Lecture lecture;

    private static Course course;

    private static final Long COURSE_ID = 27L;

    /**
     * Sets up all needed mocks and their wanted behavior once for all test cases.
     * These are the common mocks and structures which behavior is fixed and will not change
     */
    @BeforeAll
    public static void setUp() {
        mailService = mock(MailService.class);
        doNothing().when(mailService).sendNotificationEmailForMultipleUsers(any(), any(), any());

        course = new Course();
        course.setId(COURSE_ID);

        notificationCaptor = ArgumentCaptor.forClass(Notification.class);

        messagingTemplate = mock(SimpMessageSendingOperations.class);

        notificationSettingsService = mock(NotificationSettingsService.class);

        singleUserNotificationRepository = mock(SingleUserNotificationRepository.class);

        singleUserNotificationService = spy(new SingleUserNotificationService(singleUserNotificationRepository, messagingTemplate, mailService, notificationSettingsService));

        exercise = new TextExercise();

        fileUploadExercise = new FileUploadExercise();
        fileUploadExercise.setCourse(course);

        user = new User();

        lecture = new Lecture();
        lecture.setCourse(course);

        post = new Post();
        post.setExercise(exercise);
        post.setLecture(lecture);
        post.setAuthor(user);
        post.setCourse(course);
    }

    /**
     * Prepares and cleans the mocks that are modified during the tests
     */
    @BeforeEach
    public void cleanMocks() {
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
        assertThat(capturedNotification.getTitle()).as("Title of the captured notification should be equal to the expected one").isEqualTo(expectedNotificationTitle);
    }

    /// General notify Tests

    /**
     * Tests if no notification (or email) is send if the settings are deactivated
     * However, the notification has to be saved to the DB
     */
    @Test
    public void testSendNoNotificationOrEmailWhenSettingsAreDeactivated() {
        when(notificationSettingsService.checkIfNotificationOrEmailIsAllowedBySettingsForGivenUser(any(), any(), any())).thenReturn(false);
        singleUserNotificationService.notifyUserAboutNewAnswerForExercise(post, course);
        verify(singleUserNotificationRepository, times(1)).save(notificationCaptor.capture());
        verify(messagingTemplate, times(0)).convertAndSend(any());
    }

    /**
     * Test for notifyStudentGroupAboutAttachmentChange method
     */
    @Test
    public void testNotifyUserAboutNewAnswerForExercise() {
        singleUserNotificationService.notifyUserAboutNewAnswerForExercise(post, course);
        verifyRepositoryCallWithCorrectNotification(NEW_REPLY_FOR_EXERCISE_POST_TITLE);
    }

    /**
     * Test for notifyUserAboutNewAnswerForLecture method
     */
    @Test
    public void testNotifyUserAboutNewAnswerForLecture() {
        singleUserNotificationService.notifyUserAboutNewAnswerForLecture(post, course);
        verifyRepositoryCallWithCorrectNotification(NEW_REPLY_FOR_LECTURE_POST_TITLE);
    }

    /**
     * Test for notifyUserAboutNewAnswerForCoursePost method
     */
    @Test
    public void testNotifyUserAboutNewAnswerForCoursePost() {
        singleUserNotificationService.notifyUserAboutNewAnswerForCoursePost(post, course);
        verifyRepositoryCallWithCorrectNotification(NEW_REPLY_FOR_COURSE_POST_TITLE);
    }

    /**
     * Test for notifyUserAboutSuccessfulFileUploadSubmission method
     */
    @Test
    public void testNotifyUserAboutSuccessfulFileUploadSubmission() {
        singleUserNotificationService.notifyUserAboutSuccessfulFileUploadSubmission(fileUploadExercise, user);
        verifyRepositoryCallWithCorrectNotification(FILE_SUBMISSION_SUCCESSFUL_TITLE);
    }

    /// Save & Send related Tests

    /**
     * Test for saveAndSend method for an exam exercise update (notification)
     * Checks if the notification target contains the problem statement for transmitting it to the user via WebSocket
     */
    @Test
    public void testSaveAndSend_CourseRelatedNotifications() {
        when(notificationSettingsService.checkNotificationTypeForEmailSupport(any())).thenReturn(true);
        when(notificationSettingsService.checkIfNotificationOrEmailIsAllowedBySettingsForGivenUser(any(), any(), any())).thenReturn(true);

        singleUserNotificationService.notifyUserAboutNewAnswerForCoursePost(post, course);

        // inside public saveAndSend method
        verify(singleUserNotificationRepository, times(1)).save(any());
        verify(messagingTemplate, times(1)).convertAndSend(any(), (Notification) any());

        // inside private prepareSingleUserNotificationEmail method
        verify(notificationSettingsService, times(1)).checkNotificationTypeForEmailSupport(any());
        verify(notificationSettingsService, times(2)).checkIfNotificationOrEmailIsAllowedBySettingsForGivenUser(any(), any(), any()); // 2 because we check once for webapp & email
        verify(mailService, times(1)).sendNotificationEmail(any(), any(), any());
    }
}
