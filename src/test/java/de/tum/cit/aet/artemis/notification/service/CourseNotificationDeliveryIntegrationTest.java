package de.tum.cit.aet.artemis.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsageCollector;
import de.tum.cit.aet.artemis.notification.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.ExerciseUpdatedNotification;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.notification.test_repository.CourseNotificationParameterTestRepository;
import de.tum.cit.aet.artemis.notification.test_repository.CourseNotificationTestRepository;
import de.tum.cit.aet.artemis.notification.test_repository.UserCourseNotificationStatusTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseNotificationDeliveryIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "cndelivery";

    @Autowired
    private CourseNotificationRegistryService courseNotificationRegistryService;

    @Autowired
    private CourseNotificationSettingService courseNotificationSettingService;

    @Autowired
    private CourseNotificationTestRepository courseNotificationRepository;

    @Autowired
    private CourseNotificationParameterTestRepository courseNotificationParameterRepository;

    @Autowired
    private UserCourseNotificationStatusService userCourseNotificationStatusService;

    @Autowired
    private UserCourseNotificationStatusTestRepository userCourseNotificationStatusRepository;

    @Autowired
    private FeatureUsageCollector featureUsageCollector;

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void markingNotificationSeenDuringDeliveryPersists() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        var course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        var recipient = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var notification = new ExerciseUpdatedNotification(course.getId(), course.getTitle(), course.getCourseIcon(), 1L, "Exercise", null, null, "text", null);

        var channels = new SynchronousDeliveryChannels(delivered -> {
            // Model a client displaying the websocket notification immediately, before delivery returns.
            userCourseNotificationStatusService.markDisplayedNotificationsAsSeen(recipient, List.of(delivered.notificationId()), course.getId());
        });
        var service = new CourseNotificationService(courseNotificationRegistryService, courseNotificationSettingService, courseNotificationRepository,
                courseNotificationParameterRepository, userCourseNotificationStatusService, channels.webappService, channels.pushService, channels.emailService,
                featureUsageCollector);

        service.sendCourseNotification(notification, List.of(recipient));

        assertThat(userCourseNotificationStatusRepository.findAllByCourseNotificationId(notification.notificationId)).singleElement()
                .satisfies(status -> assertThat(status.getStatus()).isEqualTo(UserCourseNotificationStatusType.SEEN));
    }

    /** Delivery doubles complete inline, without Spring's asynchronous proxies or real websocket, push and email work. */
    private static class SynchronousDeliveryChannels {

        private final CourseNotificationWebappService webappService = mock(CourseNotificationWebappService.class);

        private final CourseNotificationPushService pushService = mock(CourseNotificationPushService.class);

        private final CourseNotificationEmailService emailService = mock(CourseNotificationEmailService.class);

        private SynchronousDeliveryChannels(Consumer<CourseNotificationDTO> webappClient) {
            when(pushService.sendCourseNotification(any(CourseNotificationDTO.class), anyList())).thenReturn(CompletableFuture.completedFuture(null));
            when(emailService.sendCourseNotification(any(CourseNotificationDTO.class), anyList())).thenReturn(CompletableFuture.completedFuture(null));
            when(webappService.sendCourseNotification(any(CourseNotificationDTO.class), anyList())).thenAnswer(invocation -> {
                assertThat(invocation.<List<?>>getArgument(1)).hasSize(1);
                webappClient.accept(invocation.getArgument(0));
                return CompletableFuture.completedFuture(null);
            });
        }
    }
}
