package de.tum.cit.aet.artemis.communication.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.cit.aet.artemis.communication.domain.CourseNotification;
import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationStatus;
import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.communication.test_repository.CourseNotificationTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.UserCourseNotificationStatusTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class UserCourseNotificationStatusResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "ucnstest";

    @Autowired
    private CourseNotificationTestRepository courseNotificationRepository;

    @Autowired
    private UserCourseNotificationStatusTestRepository userCourseNotificationStatusRepository;

    private User user;

    private Course course;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        course = courseUtilService.createCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldUpdateNotificationStatusWhenUpdateIsCalled() throws Exception {
        var courseNotification = new CourseNotification(course, (short) 1, ZonedDateTime.now(), ZonedDateTime.now());
        courseNotificationRepository.save(courseNotification);

        var status = new UserCourseNotificationStatus(courseNotification, user, UserCourseNotificationStatusType.UNSEEN);
        userCourseNotificationStatusRepository.save(status);

        String requestBody = "{\"statusType\":\"SEEN\", \"notificationIds\":[" + courseNotification.getId() + "]}";

        request.performMvcRequest(
                MockMvcRequestBuilders.put("/api/communication/notification/" + course.getId() + "/status").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk());

        var newStatus = userCourseNotificationStatusRepository.findByCourseNotificationId(courseNotification.getId());

        assertThat(newStatus.getStatus()).isEqualTo(UserCourseNotificationStatusType.SEEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotUpdateNotificationStatusWhenNotExists() throws Exception {
        var courseNotification = new CourseNotification(course, (short) 1, ZonedDateTime.now(), ZonedDateTime.now());
        courseNotificationRepository.save(courseNotification);

        var status = new UserCourseNotificationStatus(courseNotification, user, UserCourseNotificationStatusType.UNSEEN);
        userCourseNotificationStatusRepository.save(status);

        String requestBody = "{\"statusType\":\"SEEN\", \"notificationIds\":[" + 999 + "]}";

        request.performMvcRequest(
                MockMvcRequestBuilders.put("/api/communication/notification/" + course.getId() + "/status").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk());

        var newStatus = userCourseNotificationStatusRepository.findByCourseNotificationId(courseNotification.getId());

        assertThat(newStatus.getStatus()).isEqualTo(UserCourseNotificationStatusType.UNSEEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldBatchUpdateNotificationStatusesWhenMultipleAreSupplied() throws Exception {
        List<CourseNotification> notifications = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            var courseNotification = new CourseNotification(course, (short) 1, ZonedDateTime.now(), ZonedDateTime.now());
            courseNotificationRepository.save(courseNotification);

            var status = new UserCourseNotificationStatus(courseNotification, user, UserCourseNotificationStatusType.UNSEEN);
            userCourseNotificationStatusRepository.save(status);

            notifications.add(courseNotification);
        }

        String requestBody = "{\"statusType\":\"SEEN\", \"notificationIds\":[" + String.join(", ", notifications.stream().map(DomainObject::getId).map(Object::toString).toList())
                + "]}";

        request.performMvcRequest(
                MockMvcRequestBuilders.put("/api/communication/notification/" + course.getId() + "/status").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk());

        for (var courseNotification : notifications) {
            var newStatus = userCourseNotificationStatusRepository.findByCourseNotificationId(courseNotification.getId());

            assertThat(newStatus.getStatus()).isEqualTo(UserCourseNotificationStatusType.SEEN);
        }
    }
}
