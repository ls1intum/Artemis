package de.tum.cit.aet.artemis.coursenotification.web;

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

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.coursenotification.domain.CourseNotification;
import de.tum.cit.aet.artemis.coursenotification.domain.NotificationSettingOption;
import de.tum.cit.aet.artemis.coursenotification.domain.UserCourseNotificationStatus;
import de.tum.cit.aet.artemis.coursenotification.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.coursenotification.domain.notifications.NewPostNotification;
import de.tum.cit.aet.artemis.coursenotification.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.coursenotification.service.CourseNotificationBroadcastService;
import de.tum.cit.aet.artemis.coursenotification.service.CourseNotificationService;
import de.tum.cit.aet.artemis.coursenotification.test_repository.CourseNotificationTestRepository;
import de.tum.cit.aet.artemis.coursenotification.test_repository.UserCourseNotificationStatusTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseNotificationResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "cntest";

    @Autowired
    private CourseNotificationTestRepository courseNotificationRepository;

    @Autowired
    private UserCourseNotificationStatusTestRepository userCourseNotificationStatusTestRepository;

    @Autowired
    private CourseNotificationService courseNotificationService;

    private User user;

    private Course course;

    @BeforeEach
    void setUp() {
        user = userUtilService.createAndSaveUser(TEST_PREFIX + "student1");
        course = courseUtilService.createCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnNotificationsWhenGetCourseNotificationsIsCalled() throws Exception {
        var courseNotification = new CourseNotification(course, (short) 1, ZonedDateTime.now(), ZonedDateTime.now());

        courseNotificationRepository.save(courseNotification);

        var userCourseNotificationStatus = new UserCourseNotificationStatus(courseNotification, user, UserCourseNotificationStatusType.UNSEEN);

        userCourseNotificationStatusTestRepository.save(userCourseNotificationStatus);

        request.performMvcRequest(MockMvcRequestBuilders.get("/api/coursenotification/course/" + course.getId() + "?page=0&size=20")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.content[0].notificationType").value("newPostNotification"))
                .andExpect(jsonPath("$.content[0].courseId").value(course.getId()));
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
        mockServiceMap.put(NotificationSettingOption.WEBAPP, noopService);
        mockServiceMap.put(NotificationSettingOption.PUSH, noopService);
        mockServiceMap.put(NotificationSettingOption.EMAIL, noopService);

        ReflectionTestUtils.setField(courseNotificationService, "serviceMap", mockServiceMap);

        var notification = new NewPostNotification(course.getId(), course.getTitle(), course.getCourseIcon(), 1L, "test test", 1L, "Test Channel", "coursewide", "Test Author",
                "image.url", 1L);

        courseNotificationService.sendCourseNotification(notification, List.of(user));

        request.performMvcRequest(MockMvcRequestBuilders.get("/api/coursenotification/course/" + course.getId() + "?page=0&size=20")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.content[0].notificationType").value("newPostNotification"))
                .andExpect(jsonPath("$.content[0].courseId").value(course.getId())).andExpect(jsonPath("$.content[0].parameters['channelName']").value("Test Channel"))
                .andExpect(jsonPath("$.content[0].parameters['courseTitle']").value(course.getTitle()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnEmptyResultWhenNoNotificationsAreFound() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/coursenotification/course/" + course.getId() + "?page=0&size=20")).andExpect(status().isOk())
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

        request.performMvcRequest(MockMvcRequestBuilders.get("/api/coursenotification/course/" + course.getId() + "?page=0&size=" + pageSize)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(pageSize)));
    }
}
