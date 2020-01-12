package de.tum.in.www1.artemis;

import java.util.List;

import javax.swing.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.GroupNotification;
import de.tum.in.www1.artemis.domain.Notification;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.NotificationRepository;
import de.tum.in.www1.artemis.repository.SystemNotificationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class NotificationResourceIntegrationTest extends AbstractSpringIntegrationTest {

    Exercise exercise;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SystemNotificationRepository systemNotificationRepository;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(1, 1, 1);
        database.addCourseWithOneTextExercise();
        exercise = exerciseRepo.findAll().get(0);
    }

    @AfterEach
    public void tearDown() throws Exception {
        database.resetDatabase();
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
        request.post("/api/notifications", groupNotification, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    public void testGetAllSystemNotifications_asInstructor() throws Exception {
        GroupNotificationType type = GroupNotificationType.INSTRUCTOR;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourse(), "Title", "Notification Text", null, type);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        request.get("/notifications/for-user", HttpStatus.NOT_FOUND, List.class);
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    public void testUpdateNotification_asInstructor() throws Exception {
        GroupNotificationType type = GroupNotificationType.INSTRUCTOR;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourse(), "Title", "Notification Text", null, type);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        groupNotification.setId(1L);
        request.put("/api/notifications", groupNotification, HttpStatus.OK);
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
