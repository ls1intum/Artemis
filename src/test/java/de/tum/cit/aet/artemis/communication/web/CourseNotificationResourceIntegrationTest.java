package de.tum.cit.aet.artemis.communication.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.cit.aet.artemis.communication.domain.CourseNotification;
import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationStatus;
import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewPostNotification;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationBroadcastService;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationService;
import de.tum.cit.aet.artemis.communication.test_repository.CourseNotificationTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.UserCourseNotificationStatusTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
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
        featureToggleService.enableFeature(Feature.CourseSpecificNotifications);
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        course = courseUtilService.createCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnNotificationsWhenGetCourseNotificationsIsCalled() throws Exception {
        var courseNotification = new CourseNotification(course, (short) 1, ZonedDateTime.now(), ZonedDateTime.now());

        courseNotificationRepository.save(courseNotification);

        var userCourseNotificationStatus = new UserCourseNotificationStatus(courseNotification, user, UserCourseNotificationStatusType.UNSEEN);

        userCourseNotificationStatusTestRepository.save(userCourseNotificationStatus);

        request.performMvcRequest(MockMvcRequestBuilders.get("/api/communication/notification/" + course.getId() + "?page=0&size=20")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.content[0].notificationType").value("newPostNotification"))
                .andExpect(jsonPath("$.content[0].courseId").value(course.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotReturnNotificationsWhenFeatureIsDisabled() throws Exception {
        featureToggleService.disableFeature(Feature.CourseSpecificNotifications);

        var courseNotification = new CourseNotification(course, (short) 1, ZonedDateTime.now(), ZonedDateTime.now());

        courseNotificationRepository.save(courseNotification);

        var userCourseNotificationStatus = new UserCourseNotificationStatus(courseNotification, user, UserCourseNotificationStatusType.UNSEEN);

        userCourseNotificationStatusTestRepository.save(userCourseNotificationStatus);

        request.performMvcRequest(MockMvcRequestBuilders.get("/api/communication/notification/" + course.getId() + "?page=0&size=20")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnNotificationWhenActualNotificationIsStored() throws Exception {
        HashMap<Object, Object> mockServiceMap = new HashMap<>();

        CourseNotificationBroadcastService noopService = new CourseNotificationBroadcastService() {

            @Override
            protected void sendCourseNotification(CourseNotificationDTO courseNotification, List<User> recipients) {
                // Do nothing
            }
        };

        // We want to test the storage and querying of notifications, so we do not need any implementation here.
        mockServiceMap.put(NotificationChannelOption.WEBAPP, noopService);
        mockServiceMap.put(NotificationChannelOption.PUSH, noopService);
        mockServiceMap.put(NotificationChannelOption.EMAIL, noopService);

        ReflectionTestUtils.setField(courseNotificationService, "serviceMap", mockServiceMap);

        var notification = new NewPostNotification(course.getId(), course.getTitle(), course.getCourseIcon(), 1L, "test test", 1L, "Test Channel", "coursewide", "Test Author",
                "image.url", 1L);

        courseNotificationService.sendCourseNotification(notification, List.of(user));

        request.performMvcRequest(MockMvcRequestBuilders.get("/api/communication/notification/" + course.getId() + "?page=0&size=20")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.content[0].notificationType").value("newPostNotification"))
                .andExpect(jsonPath("$.content[0].courseId").value(course.getId())).andExpect(jsonPath("$.content[0].parameters['channelName']").value("Test Channel"))
                .andExpect(jsonPath("$.content[0].parameters['courseTitle']").value(course.getTitle()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnEmptyResultWhenNoNotificationsAreFound() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/communication/notification/" + course.getId() + "?page=0&size=20")).andExpect(status().isOk())
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

        request.performMvcRequest(MockMvcRequestBuilders.get("/api/communication/notification/" + course.getId() + "?page=0&size=" + pageSize)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(pageSize)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnNotificationInfoWhenGetCourseNotificationInfoIsCalled() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/communication/notification/info")).andExpect(status().isOk()).andExpect(jsonPath("$.presets").isArray())
                .andExpect(jsonPath("$.presets").isNotEmpty())
                .andExpect(jsonPath("$.channels[*]").value(org.hamcrest.Matchers.containsInAnyOrder(NotificationChannelOption.values()[0].name(),
                        NotificationChannelOption.values()[1].name(), NotificationChannelOption.values()[2].name())))
                .andExpect(jsonPath("$.notificationTypes").isArray()).andExpect(jsonPath("$.notificationTypes").isNotEmpty());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotReturnNotificationInfoWhenFeatureIsDisabled() throws Exception {
        featureToggleService.disableFeature(Feature.CourseSpecificNotifications);

        request.performMvcRequest(MockMvcRequestBuilders.get("/api/communication/notification/info")).andExpect(status().isForbidden());
    }
}
