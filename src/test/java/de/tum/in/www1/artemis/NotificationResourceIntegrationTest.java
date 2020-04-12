package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.jdbc.Sql;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class NotificationResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    UserService userService;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    GroupNotificationRepository groupNotificationRepository;

    @Autowired
    SingleUserNotificationRepository singleUserNotificationRepository;

    @Autowired
    RequestUtilService request;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SystemNotificationRepository systemNotificationRepository;

    @Autowired
    SystemNotificationService systemNotificationService;

    private Exercise exercise;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(1, 1, 1);
        database.addCourseWithOneTextExercise();
        systemNotificationRepository.deleteAll();
        exercise = exerciseRepo.findAll().get(0);
    }

    @AfterEach
    public void tearDown() throws Exception {
        database.resetDatabase();
        systemNotificationRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "USER")
    public void testCreateNotification_asUser() throws Exception {
        GroupNotificationType type = GroupNotificationType.STUDENT;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourse(), "Title", "Notification Text", null, type);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        request.post("/api/notifications", groupNotification, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    public void testCreateNotification_asInstructor() throws Exception {
        GroupNotificationType type = GroupNotificationType.INSTRUCTOR;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourse(), "Title", "Notification Text", null, type);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));

        GroupNotification response = request.postWithResponseBody("/api/notifications", groupNotification, GroupNotification.class, HttpStatus.CREATED);
        assertThat(response.getTarget()).as("response same target").isEqualTo(groupNotification.getTarget());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    public void testCreateNotification_asInstructor_BAD_REQUEST() throws Exception {
        GroupNotificationType type = GroupNotificationType.INSTRUCTOR;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourse(), "Title", "Notification Text", null, type);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        groupNotification.setId(1L);
        request.post("/api/notifications", groupNotification, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetNotifications_asInstructor() throws Exception {
        GroupNotificationType type = GroupNotificationType.INSTRUCTOR;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourse(), "Title", "Notification Text", null, type);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        notificationRepository.save(groupNotification);
        List<Notification> notificationResponse = request.get("/api/notifications", HttpStatus.OK, List.class);
        assertThat(notificationResponse.size()).as("response length is 1").isEqualTo(1);
    }

    @Test
    @Sql({ "/h2/custom-functions.sql" })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetSystemNotifications() throws Exception {
        SystemNotification systemNotification = ModelFactory.generateSystemNotification(ZonedDateTime.now().plusDays(1), ZonedDateTime.now().minusDays(1));
        systemNotificationRepository.save(systemNotification);

        GroupNotificationType type = GroupNotificationType.INSTRUCTOR;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourse(), "Title", "Notification Text", null, type);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        groupNotificationRepository.save(groupNotification);

        User author = userService.getUserWithGroupsAndAuthorities();
        User recipient = userService.getUserWithGroupsAndAuthorities();

        SingleUserNotification singleUserNotification = new SingleUserNotification(recipient, author, "title", "text");
        singleUserNotificationRepository.save(singleUserNotification);

        List<SystemNotification> response = request.get("/api/notifications/for-user", HttpStatus.OK, List.class);
        assertThat(response.isEmpty()).as("response is not empty").isFalse();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    public void testUpdateNotification_asInstructor_OK() throws Exception {
        GroupNotificationType type = GroupNotificationType.INSTRUCTOR;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourse(), "Title", "Notification Text", null, type);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        groupNotification.setId(1L);
        request.put("/api/notifications", groupNotification, HttpStatus.OK);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    public void testUpdateNotification_asInstructor_BAD_REQUEST() throws Exception {
        GroupNotificationType type = GroupNotificationType.INSTRUCTOR;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourse(), "Title", "Notification Text", null, type);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        request.putWithResponseBody("/api/notifications", groupNotification, GroupNotification.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(roles = "USER")
    public void testUpdateNotification_asStudent() throws Exception {
        GroupNotificationType type = GroupNotificationType.STUDENT;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourse(), "Title", "Notification Text", null, type);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        groupNotification.setId(2L);
        request.put("/api/notifications", groupNotification, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    public void testGetNotification_asInstructor() throws Exception {
        GroupNotificationType type = GroupNotificationType.INSTRUCTOR;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourse(), "Title", "Notification Text", null, type);
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
        GroupNotification groupNotification = new GroupNotification(exercise.getCourse(), "Title", "Notification Text", null, type);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        Notification notification = request.postWithResponseBody("/api/notifications", groupNotification, Notification.class, HttpStatus.CREATED);
        request.put("/api/notifications", notification, HttpStatus.OK);
        request.delete("/api/notifications/" + notification.getId(), HttpStatus.OK);
    }
}
