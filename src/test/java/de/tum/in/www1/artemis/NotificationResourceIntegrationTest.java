package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;

public class NotificationResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private SystemNotificationRepository systemNotificationRepository;

    private Exercise exercise;

    private Course course1;

    private Course course2;

    private List<User> users;

    private NotificationType notificationType = NotificationType.LEGACY;

    @BeforeEach
    public void initTestCase() {
        users = database.addUsers(2, 1, 1, 1);
        course1 = database.addCourseWithOneReleasedTextExercise();
        course2 = database.addCourseWithOneReleasedTextExercise();
        systemNotificationRepository.deleteAll();
        exercise = new ArrayList<>(course1.getExercises()).get(0);

        User student1 = users.get(0);
        student1.setLastNotificationRead(ZonedDateTime.now().minusDays(1));
        users.set(0, student1);
        userRepository.save(student1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        systemNotificationRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "USER")
    public void testCreateNotification_asUser() throws Exception {
        GroupNotificationType type = GroupNotificationType.STUDENT;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourseViaExerciseGroupOrCourseMember(), "Title", "Notification Text", null, type, notificationType);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        request.post("/api/notifications", groupNotification, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    public void testCreateNotification_asInstructor() throws Exception {
        GroupNotificationType type = GroupNotificationType.INSTRUCTOR;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourseViaExerciseGroupOrCourseMember(), "Title", "Notification Text", null, type, notificationType);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        GroupNotification response = request.postWithResponseBody("/api/notifications", groupNotification, GroupNotification.class, HttpStatus.CREATED);
        assertThat(response.getTarget()).as("response same target").isEqualTo(groupNotification.getTarget());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    public void testCreateNotification_asInstructor_BAD_REQUEST() throws Exception {
        GroupNotificationType type = GroupNotificationType.INSTRUCTOR;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourseViaExerciseGroupOrCourseMember(), "Title", "Notification Text", null, type, notificationType);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        groupNotification.setId(1L);
        request.post("/api/notifications", groupNotification, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetNotifications_recipientEvaluation() throws Exception {
        User recipient = userRepository.getUser();
        SingleUserNotification notification1 = ModelFactory.generateSingleUserNotification(ZonedDateTime.now(), recipient);
        notificationRepository.save(notification1);
        SingleUserNotification notification2 = ModelFactory.generateSingleUserNotification(ZonedDateTime.now(), users.get(1));
        notificationRepository.save(notification2);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Notification with recipient equal to current user is returned").contains(notification1);
        assertThat(notifications).as("Notification with recipient not equal to current user is not returned").doesNotContain(notification2);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetNotifications_courseEvaluation() throws Exception {
        // student1 is member of `testgroup` and `tumuser` per default
        // the studentGroupName of course1 is `tumuser` per default
        GroupNotification notification1 = ModelFactory.generateGroupNotification(ZonedDateTime.now(), course1, GroupNotificationType.STUDENT);
        notificationRepository.save(notification1);
        course2.setStudentGroupName("some-group");
        courseRepository.save(course2);
        GroupNotification notification2 = ModelFactory.generateGroupNotification(ZonedDateTime.now(), course2, GroupNotificationType.STUDENT);
        notificationRepository.save(notification2);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Notification with course the current user belongs to is returned").contains(notification1);
        assertThat(notifications).as("Notification with course the current user does not belong to is not returned").doesNotContain(notification2);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetNotifications_groupNotificationTypeEvaluation_asStudent() throws Exception {
        GroupNotification notificationStudent = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.STUDENT);
        notificationRepository.save(notificationStudent);
        GroupNotification notificationTutor = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.TA);
        notificationRepository.save(notificationTutor);
        GroupNotification notificationEditor = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.EDITOR);
        notificationRepository.save(notificationEditor);
        GroupNotification notificationInstructor = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.INSTRUCTOR);
        notificationRepository.save(notificationInstructor);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Notification with type student is returned").contains(notificationStudent);
        assertThat(notifications).as("Notification with type tutor is not returned").doesNotContain(notificationTutor);
        assertThat(notifications).as("Notification with type editor is not returned").doesNotContain(notificationEditor);
        assertThat(notifications).as("Notification with type instructor is not returned").doesNotContain(notificationInstructor);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetNotifications_groupNotificationTypeEvaluation_asTutor() throws Exception {
        GroupNotification notificationStudent = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.STUDENT);
        notificationRepository.save(notificationStudent);
        GroupNotification notificationTutor = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.TA);
        notificationRepository.save(notificationTutor);
        GroupNotification notificationEditor = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.EDITOR);
        notificationRepository.save(notificationEditor);
        GroupNotification notificationInstructor = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.INSTRUCTOR);
        notificationRepository.save(notificationInstructor);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Notification with type student is not returned").doesNotContain(notificationStudent);
        assertThat(notifications).as("Notification with type tutor is returned").contains(notificationTutor);
        assertThat(notifications).as("Notification with type editor is not returned").doesNotContain(notificationEditor);
        assertThat(notifications).as("Notification with type instructor is not returned").doesNotContain(notificationInstructor);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testGetNotifications_groupNotificationTypeEvaluation_asEditor() throws Exception {
        GroupNotification notificationStudent = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.STUDENT);
        notificationRepository.save(notificationStudent);
        GroupNotification notificationTutor = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.TA);
        notificationRepository.save(notificationTutor);
        GroupNotification notificationEditor = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.EDITOR);
        notificationRepository.save(notificationEditor);
        GroupNotification notificationInstructor = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.INSTRUCTOR);
        notificationRepository.save(notificationInstructor);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Notification with type student is not returned").doesNotContain(notificationStudent);
        assertThat(notifications).as("Notification with type tutor is not returned").doesNotContain(notificationTutor);
        assertThat(notifications).as("Notification with type editor is returned").contains(notificationEditor);
        assertThat(notifications).as("Notification with type instructor is not returned").doesNotContain(notificationInstructor);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetNotifications_groupNotificationTypeEvaluation_asInstructor() throws Exception {
        GroupNotification notificationStudent = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.STUDENT);
        notificationRepository.save(notificationStudent);
        GroupNotification notificationTutor = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.TA);
        notificationRepository.save(notificationTutor);
        GroupNotification notificationEditor = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.EDITOR);
        notificationRepository.save(notificationEditor);
        GroupNotification notificationInstructor = ModelFactory.generateGroupNotification(ZonedDateTime.now(), courseRepository.findAll().get(0), GroupNotificationType.INSTRUCTOR);
        notificationRepository.save(notificationInstructor);

        List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        assertThat(notifications).as("Notification with type student is not returned").doesNotContain(notificationStudent);
        assertThat(notifications).as("Notification with type tutor is not returned").doesNotContain(notificationTutor);
        assertThat(notifications).as("Notification with type editor is not returned").doesNotContain(notificationEditor);
        assertThat(notifications).as("Notification with type instructor is returned").contains(notificationInstructor);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    public void testUpdateNotification_asInstructor_OK() throws Exception {
        GroupNotificationType type = GroupNotificationType.INSTRUCTOR;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourseViaExerciseGroupOrCourseMember(), "Title", "Notification Text", null, type, notificationType);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        groupNotification.setId(1L);
        request.put("/api/notifications", groupNotification, HttpStatus.OK);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    public void testUpdateNotification_asInstructor_BAD_REQUEST() throws Exception {
        GroupNotificationType type = GroupNotificationType.INSTRUCTOR;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourseViaExerciseGroupOrCourseMember(), "Title", "Notification Text", null, type, notificationType);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        request.putWithResponseBody("/api/notifications", groupNotification, GroupNotification.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(roles = "USER")
    public void testUpdateNotification_asStudent() throws Exception {
        GroupNotificationType type = GroupNotificationType.STUDENT;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourseViaExerciseGroupOrCourseMember(), "Title", "Notification Text", null, type, notificationType);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        groupNotification.setId(2L);
        request.put("/api/notifications", groupNotification, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    public void testGetNotification_asInstructor() throws Exception {
        GroupNotificationType type = GroupNotificationType.INSTRUCTOR;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourseViaExerciseGroupOrCourseMember(), "Title", "Notification Text", null, type, notificationType);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        Notification notification = request.postWithResponseBody("/api/notifications", groupNotification, Notification.class, HttpStatus.CREATED);
        request.put("/api/notifications", notification, HttpStatus.OK);
        request.get("/api/notifications/" + notification.getId(), HttpStatus.OK, Notification.class);
        request.get("/api/notifications/" + notification.getId() + 1, HttpStatus.NOT_FOUND, Notification.class);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    public void testDeleteNotification_asInstructor() throws Exception {
        GroupNotificationType type = GroupNotificationType.INSTRUCTOR;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourseViaExerciseGroupOrCourseMember(), "Title", "Notification Text", null, type, notificationType);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        Notification notification = request.postWithResponseBody("/api/notifications", groupNotification, Notification.class, HttpStatus.CREATED);
        request.put("/api/notifications", notification, HttpStatus.OK);
        request.delete("/api/notifications/" + notification.getId(), HttpStatus.OK);
    }
}
