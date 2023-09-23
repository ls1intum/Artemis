package de.tum.in.www1.artemis.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.NotificationSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.NotificationConstants;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.user.UserUtilService;

class NotificationResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private Course course1;

    private Course course2;

    private static final String TEST_PREFIX = "notificationresource";

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        course1 = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        course2 = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        notificationRepository.deleteAll();

        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        student1.setLastNotificationRead(ZonedDateTime.now().minusDays(1));
        userRepository.save(student1);
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetNotifications_recipientEvaluation() throws Exception {
        User recipient = userRepository.getUser();
        SingleUserNotification notification1 = NotificationFactory.generateSingleUserNotification(ZonedDateTime.now(), recipient);
        notificationRepository.save(notification1);
        SingleUserNotification notification2 = NotificationFactory.generateSingleUserNotification(ZonedDateTime.now(), userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        notificationRepository.save(notification2);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Notification with recipient equal to current user is returned").contains(notification1);
        assertThat(notifications).as("Notification with recipient not equal to current user is not returned").doesNotContain(notification2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetNotifications_courseEvaluation() throws Exception {
        // student1 is member of `testgroup` and `tumuser` per default
        // the studentGroupName of course1 is `tumuser` per default
        GroupNotification notification1 = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.STUDENT);
        notificationRepository.save(notification1);
        course2.setStudentGroupName("some-group");
        courseRepository.save(course2);
        GroupNotification notification2 = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course2, GroupNotificationType.STUDENT);
        notificationRepository.save(notification2);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Notification with course the current user belongs to is returned").contains(notification1);
        assertThat(notifications).as("Notification with course the current user does not belong to is not returned").doesNotContain(notification2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetNotifications_groupNotificationTypeEvaluation_asStudent() throws Exception {
        GroupNotification notificationStudent = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.STUDENT);
        notificationRepository.save(notificationStudent);
        GroupNotification notificationTutor = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.TA);
        notificationRepository.save(notificationTutor);
        GroupNotification notificationEditor = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.EDITOR);
        notificationRepository.save(notificationEditor);
        GroupNotification notificationInstructor = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.INSTRUCTOR);
        notificationRepository.save(notificationInstructor);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Notification with type student is returned").contains(notificationStudent);
        assertThat(notifications).as("Notification with type tutor is not returned").doesNotContain(notificationTutor);
        assertThat(notifications).as("Notification with type editor is not returned").doesNotContain(notificationEditor);
        assertThat(notifications).as("Notification with type instructor is not returned").doesNotContain(notificationInstructor);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetNotifications_groupNotificationTypeEvaluation_asTutor() throws Exception {
        GroupNotification notificationStudent = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.STUDENT);
        notificationRepository.save(notificationStudent);
        GroupNotification notificationTutor = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.TA);
        notificationRepository.save(notificationTutor);
        GroupNotification notificationEditor = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.EDITOR);
        notificationRepository.save(notificationEditor);
        GroupNotification notificationInstructor = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.INSTRUCTOR);
        notificationRepository.save(notificationInstructor);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Notification with type student is not returned").doesNotContain(notificationStudent);
        assertThat(notifications).as("Notification with type tutor is returned").contains(notificationTutor);
        assertThat(notifications).as("Notification with type editor is not returned").doesNotContain(notificationEditor);
        assertThat(notifications).as("Notification with type instructor is not returned").doesNotContain(notificationInstructor);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetNotifications_groupNotificationTypeEvaluation_asEditor() throws Exception {
        GroupNotification notificationStudent = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.STUDENT);
        notificationRepository.save(notificationStudent);
        GroupNotification notificationTutor = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.TA);
        notificationRepository.save(notificationTutor);
        GroupNotification notificationEditor = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.EDITOR);
        notificationRepository.save(notificationEditor);
        GroupNotification notificationInstructor = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.INSTRUCTOR);
        notificationRepository.save(notificationInstructor);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Notification with type student is not returned").doesNotContain(notificationStudent);
        assertThat(notifications).as("Notification with type tutor is not returned").doesNotContain(notificationTutor);
        assertThat(notifications).as("Notification with type editor is returned").contains(notificationEditor);
        assertThat(notifications).as("Notification with type instructor is not returned").doesNotContain(notificationInstructor);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetNotifications_groupNotificationTypeEvaluation_asInstructor() throws Exception {
        GroupNotification notificationStudent = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.STUDENT);
        notificationRepository.save(notificationStudent);
        GroupNotification notificationTutor = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.TA);
        notificationRepository.save(notificationTutor);
        GroupNotification notificationEditor = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.EDITOR);
        notificationRepository.save(notificationEditor);
        GroupNotification notificationInstructor = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.INSTRUCTOR);
        notificationRepository.save(notificationInstructor);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Notification with type student is not returned").doesNotContain(notificationStudent);
        assertThat(notifications).as("Notification with type tutor is not returned").doesNotContain(notificationTutor);
        assertThat(notifications).as("Notification with type editor is not returned").doesNotContain(notificationEditor);
        assertThat(notifications).as("Notification with type instructor is returned").contains(notificationInstructor);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllNotificationsForCurrentUserFilteredBySettings() throws Exception {
        NotificationType allowedType = NotificationType.ATTACHMENT_CHANGE;
        NotificationType blockedType = NotificationType.EXERCISE_PRACTICE;

        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        NotificationSetting allowedSetting = new NotificationSetting(student1, true, false, true, "notification.lecture-notification.attachment-changes");
        NotificationSetting blockedSetting = new NotificationSetting(student1, false, false, true, "notification.exercise-notification.exercise-open-for-practice");

        notificationSettingRepository.save(allowedSetting);
        notificationSettingRepository.save(blockedSetting);

        GroupNotification allowedNotification = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.STUDENT);
        allowedNotification.setTitle(NotificationConstants.findCorrespondingNotificationTitle(allowedType));
        notificationRepository.save(allowedNotification);

        GroupNotification blockedNotification = NotificationFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.STUDENT);
        blockedNotification.setTitle(NotificationConstants.findCorrespondingNotificationTitle(blockedType));
        notificationRepository.save(blockedNotification);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);

        assertThat(notifications).as("Notification that is allowed by Settings is returned").contains(allowedNotification);
        assertThat(notifications).as("Notification that is blocked by Settings is not returned").doesNotContain(blockedNotification);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllNotificationsForCurrentUser_hideUntilDeactivated() throws Exception {
        ZonedDateTime timeNow = ZonedDateTime.now();

        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        // i.e. the show all notifications regardless of their creation/notification date
        student1.setHideNotificationsUntil(null);
        userRepository.save(student1);

        GroupNotification futureNotification = NotificationFactory.generateGroupNotification(timeNow.plusHours(1), course1, GroupNotificationType.STUDENT);
        notificationRepository.save(futureNotification);

        GroupNotification pastNotification = NotificationFactory.generateGroupNotification(timeNow.minusHours(1), course1, GroupNotificationType.STUDENT);
        notificationRepository.save(pastNotification);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);

        assertThat(notifications).as("Future notification is returned").contains(futureNotification);
        assertThat(notifications).as("Past notification is returned").contains(pastNotification);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllNotificationsForCurrentUser_hideUntilActivated() throws Exception {
        ZonedDateTime timeNow = ZonedDateTime.now();

        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        student1.setHideNotificationsUntil(timeNow);
        userRepository.save(student1);

        GroupNotification futureNotification = NotificationFactory.generateGroupNotification(timeNow.plusHours(1), course1, GroupNotificationType.STUDENT);
        notificationRepository.save(futureNotification);

        GroupNotification pastNotification = NotificationFactory.generateGroupNotification(timeNow.minusHours(1), course1, GroupNotificationType.STUDENT);
        notificationRepository.save(pastNotification);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);

        assertThat(notifications).as("Future notification is returned because it is after the hideUntil property").contains(futureNotification);
        assertThat(notifications).as("Past notification is not returned because it is prior to the hideUntil property").doesNotContain(pastNotification);
    }
}
