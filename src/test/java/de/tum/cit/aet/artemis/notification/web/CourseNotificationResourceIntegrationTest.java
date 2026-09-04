package de.tum.cit.aet.artemis.notification.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.notification.domain.CourseNotification;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationStatus;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.NewAnnouncementNotification;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationBroadcastService;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationService;
import de.tum.cit.aet.artemis.notification.test_repository.CourseNotificationTestRepository;
import de.tum.cit.aet.artemis.notification.test_repository.UserCourseNotificationStatusTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseNotificationResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "cntest";

    @Autowired
    private CourseNotificationTestRepository courseNotificationRepository;

    @Autowired
    private UserCourseNotificationStatusTestRepository userCourseNotificationStatusTestRepository;

    @Autowired
    private CourseNotificationService courseNotificationService;

    @Autowired
    private FeatureToggleService featureToggleService;

    private User user;

    private Course course;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnNotificationsWhenGetCourseNotificationsIsCalled() throws Exception {
        var courseNotification = new CourseNotification(course, (short) 1, ZonedDateTime.now(), ZonedDateTime.now());

        courseNotificationRepository.save(courseNotification);

        var userCourseNotificationStatus = new UserCourseNotificationStatus(courseNotification, user, UserCourseNotificationStatusType.UNSEEN);

        userCourseNotificationStatusTestRepository.save(userCourseNotificationStatus);

        request.performMvcRequest(MockMvcRequestBuilders.get("/api/notification/courses/" + course.getId() + "?page=0&size=20")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.content[0].notificationType").value("newPostNotification"))
                .andExpect(jsonPath("$.content[0].courseId").value(course.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnNotificationWhenActualNotificationIsStored() throws Exception {
        sendAnnouncementNotificationToUser();

        request.performMvcRequest(MockMvcRequestBuilders.get("/api/notification/courses/" + course.getId() + "?page=0&size=20")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.content[0].notificationType").value("newAnnouncementNotification"))
                .andExpect(jsonPath("$.content[0].courseId").value(course.getId()))
                // The type specific values live in the payload of the notification type, and the values every
                // notification carries are fields of their own rather than entries in a map.
                .andExpect(jsonPath("$.content[0].payload.authorName").value("Test Author")).andExpect(jsonPath("$.content[0].courseTitle").value(course.getTitle()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldStillReturnFlatParametersForClientsThatHaveNotMigrated() throws Exception {
        sendAnnouncementNotificationToUser();

        // Released iOS versions decode "parameters" as a required key of every entry of this list, so its absence
        // fails the decode of the whole page. It is written from the same values as the typed payload above.
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/notification/courses/" + course.getId() + "?page=0&size=20")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].parameters.authorName").value("Test Author")).andExpect(jsonPath("$.content[0].parameters.postId").value(1))
                .andExpect(jsonPath("$.content[0].parameters.courseTitle").value(course.getTitle()));
    }

    /**
     * Stores one notification for the test user without delivering it anywhere.
     * <p>
     * The broadcast services are replaced rather than mocked because these tests are about storing and querying
     * notifications, and every channel would otherwise reach for infrastructure none of them needs.
     */
    private void sendAnnouncementNotificationToUser() {
        HashMap<Object, Object> mockServiceMap = new HashMap<>();

        CourseNotificationBroadcastService noopService = new CourseNotificationBroadcastService() {

            @Override
            protected CompletableFuture<Void> sendCourseNotification(CourseNotificationDTO courseNotification, List<CourseNotificationRecipientDTO> recipients) {
                // Does nothing, and reports that it finished doing nothing
                return CompletableFuture.completedFuture(null);
            }
        };

        mockServiceMap.put(NotificationChannelOption.WEBAPP, noopService);
        mockServiceMap.put(NotificationChannelOption.PUSH, noopService);
        mockServiceMap.put(NotificationChannelOption.EMAIL, noopService);

        ReflectionTestUtils.setField(courseNotificationService, "serviceMap", mockServiceMap);

        var notification = new NewAnnouncementNotification(course.getId(), course.getTitle(), course.getCourseIcon(), 1L, "test test", "test test", "Test Author", "image.url", 1L,
                1L);

        courseNotificationService.sendCourseNotification(notification, List.of(user));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnEmptyResultWhenNoNotificationsAreFound() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/notification/courses/" + course.getId() + "?page=0&size=20")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnPageSizeNotificationsWhenMoreThanPageSizeAreInDatabase() throws Exception {
        var pageSize = 10;

        for (int i = 0; i < 20; i++) {
            var courseNotification = new CourseNotification(course, (short) 1, ZonedDateTime.now(), ZonedDateTime.now());

            courseNotificationRepository.save(courseNotification);

            var userCourseNotificationStatus = new UserCourseNotificationStatus(courseNotification, user, UserCourseNotificationStatusType.UNSEEN);

            userCourseNotificationStatusTestRepository.save(userCourseNotificationStatus);
        }

        request.performMvcRequest(MockMvcRequestBuilders.get("/api/notification/courses/" + course.getId() + "?page=0&size=" + pageSize)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(pageSize)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnNotificationInfoWhenGetCourseNotificationInfoIsCalled() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/notification/courses/info")).andExpect(status().isOk()).andExpect(jsonPath("$.presets").isArray())
                .andExpect(jsonPath("$.presets").isNotEmpty())
                .andExpect(jsonPath("$.channels[*]").value(org.hamcrest.Matchers.containsInAnyOrder(NotificationChannelOption.values()[0].name(),
                        NotificationChannelOption.values()[1].name(), NotificationChannelOption.values()[2].name())))
                .andExpect(jsonPath("$.notificationTypes").isNotEmpty());
    }
}
