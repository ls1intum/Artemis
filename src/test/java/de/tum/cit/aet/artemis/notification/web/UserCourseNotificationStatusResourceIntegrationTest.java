package de.tum.cit.aet.artemis.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.notification.domain.CourseNotification;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationStatus;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.notification.test_repository.CourseNotificationTestRepository;
import de.tum.cit.aet.artemis.notification.test_repository.UserCourseNotificationStatusTestRepository;
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
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
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
                MockMvcRequestBuilders.put("/api/notification/courses/" + course.getId() + "/status").contentType(MediaType.APPLICATION_JSON).content(requestBody))
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
                MockMvcRequestBuilders.put("/api/notification/courses/" + course.getId() + "/status").contentType(MediaType.APPLICATION_JSON).content(requestBody))
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
                MockMvcRequestBuilders.put("/api/notification/courses/" + course.getId() + "/status").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk());

        for (var courseNotification : notifications) {
            var newStatus = userCourseNotificationStatusRepository.findByCourseNotificationId(courseNotification.getId());

            assertThat(newStatus.getStatus()).isEqualTo(UserCourseNotificationStatusType.SEEN);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldMarkOnlyTheDisplayedNotificationsAsSeen() throws Exception {
        var displayed = new CourseNotification(course, (short) 1, ZonedDateTime.now(), ZonedDateTime.now());
        var notDisplayed = new CourseNotification(course, (short) 1, ZonedDateTime.now(), ZonedDateTime.now());
        courseNotificationRepository.save(displayed);
        courseNotificationRepository.save(notDisplayed);
        userCourseNotificationStatusRepository.save(new UserCourseNotificationStatus(displayed, user, UserCourseNotificationStatusType.UNSEEN));
        userCourseNotificationStatusRepository.save(new UserCourseNotificationStatus(notDisplayed, user, UserCourseNotificationStatusType.UNSEEN));

        String requestBody = "{\"notificationIds\":[" + displayed.getId() + "]}";

        request.performMvcRequest(MockMvcRequestBuilders.put("/api/notification/courses/" + course.getId() + "/seen").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk());

        assertThat(userCourseNotificationStatusRepository.findByCourseNotificationId(displayed.getId()).getStatus()).isEqualTo(UserCourseNotificationStatusType.SEEN);
        assertThat(userCourseNotificationStatusRepository.findByCourseNotificationId(notDisplayed.getId()).getStatus()).isEqualTo(UserCourseNotificationStatusType.UNSEEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldArchiveAllNotificationsWhenArchiveAllIsCalled() throws Exception {
        List<CourseNotification> notifications = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            var courseNotification = new CourseNotification(course, (short) 1, ZonedDateTime.now(), ZonedDateTime.now());
            courseNotificationRepository.save(courseNotification);

            var status = new UserCourseNotificationStatus(courseNotification, user, UserCourseNotificationStatusType.UNSEEN);
            userCourseNotificationStatusRepository.save(status);

            notifications.add(courseNotification);
        }

        request.performMvcRequest(MockMvcRequestBuilders.put("/api/notification/courses/" + course.getId() + "/archive-all").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        for (var courseNotification : notifications) {
            var newStatus = userCourseNotificationStatusRepository.findByCourseNotificationId(courseNotification.getId());
            assertThat(newStatus.getStatus()).isEqualTo(UserCourseNotificationStatusType.ARCHIVED);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldApplySelectiveUpdatesOnlyToCurrentUsersRows() throws Exception {
        userUtilService.addUsers(TEST_PREFIX + "other", 1, 0, 0, 0);
        User other = userUtilService.getUserByLogin(TEST_PREFIX + "otherstudent1");
        var notification = courseNotificationRepository.save(new CourseNotification(course, (short) 1, ZonedDateTime.now(), ZonedDateTime.now()));
        var own = userCourseNotificationStatusRepository.save(new UserCourseNotificationStatus(notification, user, UserCourseNotificationStatusType.UNSEEN));
        var others = userCourseNotificationStatusRepository.save(new UserCourseNotificationStatus(notification, other, UserCourseNotificationStatusType.UNSEEN));
        request.performMvcRequest(MockMvcRequestBuilders.put("/api/notification/courses/" + course.getId() + "/seen").contentType(MediaType.APPLICATION_JSON)
                .content("{\"notificationIds\":[" + notification.getId() + "]}")).andExpect(status().isOk()).andExpect(content().string(""));
        assertThat(readStatus(own)).isEqualTo(UserCourseNotificationStatusType.SEEN);
        assertThat(readStatus(others)).isEqualTo(UserCourseNotificationStatusType.UNSEEN);
        request.performMvcRequest(MockMvcRequestBuilders.put("/api/notification/courses/" + course.getId() + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"statusType\":\"ARCHIVED\",\"notificationIds\":[" + notification.getId() + "]}")).andExpect(status().isOk()).andExpect(content().string(""));
        assertThat(readStatus(own)).isEqualTo(UserCourseNotificationStatusType.ARCHIVED);
        assertThat(readStatus(others)).isEqualTo(UserCourseNotificationStatusType.UNSEEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldArchiveOnlyCurrentUsersRowsInRequestedCourseWithoutRequestBody() throws Exception {
        userUtilService.addUsers(TEST_PREFIX + "other", 1, 0, 0, 0);
        User other = userUtilService.getUserByLogin(TEST_PREFIX + "otherstudent1");
        var otherCourse = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        var notification = courseNotificationRepository.save(new CourseNotification(course, (short) 1, ZonedDateTime.now(), ZonedDateTime.now()));
        var otherNotification = courseNotificationRepository.save(new CourseNotification(otherCourse, (short) 1, ZonedDateTime.now(), ZonedDateTime.now()));
        var own = userCourseNotificationStatusRepository.save(new UserCourseNotificationStatus(notification, user, UserCourseNotificationStatusType.UNSEEN));
        var others = userCourseNotificationStatusRepository.save(new UserCourseNotificationStatus(notification, other, UserCourseNotificationStatusType.UNSEEN));
        var otherCourseStatus = userCourseNotificationStatusRepository.save(new UserCourseNotificationStatus(otherNotification, user, UserCourseNotificationStatusType.UNSEEN));
        request.performMvcRequest(MockMvcRequestBuilders.put("/api/notification/courses/" + course.getId() + "/archive-all")).andExpect(status().isOk())
                .andExpect(content().string(""));
        assertThat(readStatus(own)).isEqualTo(UserCourseNotificationStatusType.ARCHIVED);
        assertThat(readStatus(others)).isEqualTo(UserCourseNotificationStatusType.UNSEEN);
        assertThat(readStatus(otherCourseStatus)).isEqualTo(UserCourseNotificationStatusType.UNSEEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldDenyAllThreeWritesForNonmember() throws Exception {
        var otherCourse = courseUtilService.createCourse();
        for (String operation : List.of("status", "seen", "archive-all")) {
            request.performMvcRequest(MockMvcRequestBuilders.put("/api/notification/courses/" + otherCourse.getId() + "/" + operation).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"statusType\":\"SEEN\",\"notificationIds\":[]}")).andExpect(status().isForbidden());
        }
    }

    private UserCourseNotificationStatusType readStatus(UserCourseNotificationStatus stored) {
        return userCourseNotificationStatusRepository.findAllByCourseNotificationId(stored.getCourseNotification().getId()).stream()
                .filter(row -> row.getId().equals(stored.getId())).findFirst().orElseThrow().getStatus();
    }

}
