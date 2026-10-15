package de.tum.cit.aet.artemis.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.ExerciseUpdatedNotification;
import de.tum.cit.aet.artemis.notification.test_repository.UserCourseNotificationStatusTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Sending a course notification is part of the request that edits an exercise, so what it costs in database round trips
 * is paid by the person editing. The rows it writes have identifiers the application assigns, which made Spring Data load
 * every row before inserting it, one select per recipient and per parameter.
 */
class CourseNotificationQueryCountIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "cnquerycount";

    private static final int RECIPIENTS = 30;

    /**
     * What sending costs however many people receive it: the notification and its parameters, the settings of the recipients, and the status rows, which are inserted
     * in batches. A query per recipient would exceed it several times over.
     */
    private static final int QUERIES_INDEPENDENT_OF_THE_RECIPIENTS = 12;

    @Autowired
    private CourseNotificationService courseNotificationService;

    @Autowired
    private UserCourseNotificationStatusTestRepository userCourseNotificationStatusRepository;

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendingToManyRecipientsDoesNotCostAQueryPerRecipient() {
        userUtilService.addUsers(TEST_PREFIX, RECIPIENTS, 0, 0, 0);
        Course course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        List<User> recipients = IntStream.rangeClosed(1, RECIPIENTS).mapToObj(i -> userUtilService.getUserByLogin(TEST_PREFIX + "student" + i)).toList();
        var notification = new ExerciseUpdatedNotification(course.getId(), course.getTitle(), course.getCourseIcon(), 1L, "Exercise", null, null, "text", null);

        assertThatDb(() -> {
            courseNotificationService.sendCourseNotification(notification, recipients);
            return null;
        }).hasBeenCalledAtMostTimes(QUERIES_INDEPENDENT_OF_THE_RECIPIENTS);

        // every recipient has exactly one status for this notification, which other rows in the table cannot satisfy
        assertThat(userCourseNotificationStatusRepository.findAllByCourseNotificationId(notification.notificationId)).extracting(status -> status.getUser().getId())
                .containsExactlyInAnyOrderElementsOf(recipients.stream().map(User::getId).toList());
    }
}
