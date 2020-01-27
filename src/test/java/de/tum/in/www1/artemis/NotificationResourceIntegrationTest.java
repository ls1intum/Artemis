package de.tum.in.www1.artemis;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;

import static org.junit.Assert.*;

public class NotificationResourceIntegrationTest extends AbstractSpringIntegrationTest {

    Exercise exercise;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    UserService userService;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    GroupNotificationRepository groupNotificationRepository;

    @Autowired
    GroupNotificationService groupNotificationService;

    @Autowired
    SingleUserNotificationService singleUserNotificationService;

    @Autowired
    SingleUserNotificationRepository singleUserNotificationRepository;

    @Autowired
    RequestUtilService request;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    NotificationService notificationService;

    @Autowired
    SystemNotificationRepository systemNotificationRepository;

    @Autowired
    SystemNotificationService systemNotificationService;

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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetNotifications_asInstructor() throws Exception {
        GroupNotificationType type = GroupNotificationType.INSTRUCTOR;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourse(), "Title", "Notification Text", null, type);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        notificationRepository.save(groupNotification);
        List<Notification> notificationResponse = request.get("/api/notifications", HttpStatus.OK, List.class);
        assertEquals(notificationResponse.size(),1);
    }

    @Test
    @WithMockUser(username ="instructor1", roles = "INSTRUCTOR")
    public void testGetSystemNotifications() throws Exception {
        SystemNotification systemNotification = ModelFactory.generateSystemNotification(ZonedDateTime.now().plusDays(1), ZonedDateTime.now().minusDays(1));
        systemNotificationRepository.save(systemNotification);

        GroupNotificationType type = GroupNotificationType.INSTRUCTOR;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourse(), "Title", "Notification Text", null, type);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        groupNotificationRepository.save(groupNotification);

        User author = userService.getUserWithGroupsAndAuthorities();
        User recipient = userService.getUserWithGroupsAndAuthorities();

        SingleUserNotification singleUserNotification = new SingleUserNotification(recipient, author , "title", "text");
        singleUserNotificationRepository.save(singleUserNotification);

        request.get("/api/notifications/for-user", HttpStatus.INTERNAL_SERVER_ERROR, List.class);
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
