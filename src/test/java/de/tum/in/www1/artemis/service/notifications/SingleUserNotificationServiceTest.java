package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.*;
import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__EXERCISE_NOTIFICATION__FILE_SUBMISSION_SUCCESSFUL;
import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.repository.NotificationRepository;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;

public class SingleUserNotificationServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @Mock
    private JavaMailSender javaMailSender;

    @Autowired
    private SingleUserNotificationService singleUserNotificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    private Notification capturedNotification;

    private User user;

    private Exercise exercise;

    private FileUploadExercise fileUploadExercise;

    private Post post;

    private Lecture lecture;

    private Course course;

    private final Long COURSE_ID = 27L;

    /**
     * Sets up all needed mocks and their wanted behavior
     */
    @BeforeEach
    public void setUp() {
        SecurityUtils.setAuthorizationObject();

        List<User> users = database.addUsers(1, 0, 0, 0);
        user = users.get(0);

        course = new Course();
        course.setId(COURSE_ID);

        /*
         * messagingTemplate = mock(SimpMessageSendingOperations.class); javaMailSender = mock(JavaMailSender.class);
         */

        exercise = new TextExercise();

        fileUploadExercise = new FileUploadExercise();
        fileUploadExercise.setCourse(course);

        lecture = new Lecture();
        lecture.setCourse(course);

        post = new Post();
        post.setExercise(exercise);
        post.setLecture(lecture);
        post.setAuthor(user);
        post.setCourse(course);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    /**
     * Auxiliary method that checks if the groupNotificationRepository was called once successfully with the correct notification (type)
     *
     * @param expectedNotificationTitle is the title (NotificationTitleTypeConstants) of the expected notification
     */
    private void verifyRepositoryCallWithCorrectNotification(String expectedNotificationTitle) {
        capturedNotification = notificationRepository.findAll().get(0);
        assertThat(capturedNotification.getTitle()).as("Title of the captured notification should be equal to the expected one").isEqualTo(expectedNotificationTitle);
    }

    /// General notify Tests

    /**
     * Tests if no notification (or email) is sent if the settings are deactivated
     * However, the notification has to be saved to the DB
     */
    @Test
    public void testSendNoNotificationOrEmailWhenSettingsAreDeactivated() {
        notificationSettingRepository.save(new NotificationSetting(user, false, true, NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST));
        assertThat(notificationRepository.findAll().size()).as("No notifications should be present prior to the method call").isEqualTo(0);

        singleUserNotificationService.notifyUserAboutNewReplyForExercise(post, course);

        assertThat(notificationRepository.findAll().size()).as("The notification should have been saved to the DB").isEqualTo(1);
        // no web app notification or email should be sent
        verify(messagingTemplate, times(0)).convertAndSend(any());
    }

    /**
     * Test for notifyStudentGroupAboutAttachmentChange method
     */
    @Test
    public void testNotifyUserAboutNewAnswerForExercise() {
        singleUserNotificationService.notifyUserAboutNewReplyForExercise(post, course);
        verifyRepositoryCallWithCorrectNotification(NEW_REPLY_FOR_EXERCISE_POST_TITLE);
    }

    /**
     * Test for notifyUserAboutNewAnswerForLecture method
     */
    @Test
    public void testNotifyUserAboutNewAnswerForLecture() {
        singleUserNotificationService.notifyUserAboutNewReplyForLecture(post, course);
        verifyRepositoryCallWithCorrectNotification(NEW_REPLY_FOR_LECTURE_POST_TITLE);
    }

    /**
     * Test for notifyUserAboutNewAnswerForCoursePost method
     */
    @Test
    public void testNotifyUserAboutNewAnswerForCoursePost() {
        singleUserNotificationService.notifyUserAboutNewReplyForCoursePost(post, course);
        verifyRepositoryCallWithCorrectNotification(NEW_REPLY_FOR_COURSE_POST_TITLE);
    }

    /**
     * Test for notifyUserAboutSuccessfulFileUploadSubmission method
     */
    @Test
    public void testNotifyUserAboutSuccessfulFileUploadSubmission() throws InterruptedException {
        notificationSettingRepository.save(new NotificationSetting(user, true, true, NOTIFICATION__EXERCISE_NOTIFICATION__FILE_SUBMISSION_SUCCESSFUL));
        singleUserNotificationService.notifyUserAboutSuccessfulFileUploadSubmission(fileUploadExercise, user);
        verifyRepositoryCallWithCorrectNotification(FILE_SUBMISSION_SUCCESSFUL_TITLE);
        // verify(javaMailSender, timeout(1000).times(1)).send(any(MimeMessage.class));
        verify(javaMailSender, timeout(1000).times(1)).createMimeMessage();
    }
}
