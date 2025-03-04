package de.tum.cit.aet.artemis.course_notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.course_notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.course_notification.domain.NotificationSettingOption;
import de.tum.cit.aet.artemis.course_notification.domain.notifications.CourseNotification;
import de.tum.cit.aet.artemis.course_notification.domain.notifications.CourseNotificationCategory;

@ExtendWith(MockitoExtension.class)
class CourseNotificationRegistryTest {

    private CourseNotificationRegistry courseNotificationRegistry;

    @BeforeEach
    void setUp() {
        courseNotificationRegistry = new CourseNotificationRegistry();
    }

    @Test
    void shouldReturnNullWhenRequestingUnknownNotificationClass() {
        Short unknownTypeId = (short) 999;

        Class<? extends CourseNotification> result = ReflectionTestUtils.invokeMethod(courseNotificationRegistry, "getNotificationClass", unknownTypeId);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenRequestingUnknownNotificationIdentifier() {
        Class<? extends CourseNotification> unknownClass = createMockNotificationClass();

        Short result = ReflectionTestUtils.invokeMethod(courseNotificationRegistry, "getNotificationIdentifier", unknownClass);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnCorrectClassWhenRequestingKnownTypeId() {
        Short knownTypeId = (short) 1;
        Class<? extends CourseNotification> expectedClass = createTestNotificationClass();
        Map<Short, Class<? extends CourseNotification>> notificationTypes = Map.of(knownTypeId, expectedClass);
        ReflectionTestUtils.setField(courseNotificationRegistry, "notificationTypes", notificationTypes);

        Class<? extends CourseNotification> result = ReflectionTestUtils.invokeMethod(courseNotificationRegistry, "getNotificationClass", knownTypeId);

        assertThat(result).isEqualTo(expectedClass);
    }

    @Test
    void shouldReturnCorrectIdentifierWhenRequestingKnownClass() {
        Short expectedTypeId = (short) 1;
        Class<? extends CourseNotification> knownClass = createTestNotificationClass();
        Map<Class<? extends CourseNotification>, Short> notificationIdentifiers = Map.of(knownClass, expectedTypeId);
        ReflectionTestUtils.setField(courseNotificationRegistry, "notificationTypeIdentifiers", notificationIdentifiers);

        Short result = ReflectionTestUtils.invokeMethod(courseNotificationRegistry, "getNotificationIdentifier", knownClass);

        assertThat(result).isEqualTo(expectedTypeId);
    }

    /**
     * Helper method to create a test notification class
     */
    private Class<? extends CourseNotification> createTestNotificationClass() {
        return TestNotification.class;
    }

    /**
     * Helper method to create a mock notification class that's not registered
     */
    private Class<? extends CourseNotification> createMockNotificationClass() {
        return mock(CourseNotification.class).getClass();
    }

    @CourseNotificationType(1)
    static class TestNotification extends CourseNotification {

        public TestNotification(Long courseId, ZonedDateTime creationDate) {
            super(courseId, creationDate);
        }

        @Override
        public CourseNotificationCategory getCourseNotificationCategory() {
            return CourseNotificationCategory.COMMUNICATION;
        }

        @Override
        public Duration getCleanupDuration() {
            return Duration.ofDays(1);
        }

        @Override
        public List<NotificationSettingOption> getSupportedChannels() {
            return List.of(NotificationSettingOption.EMAIL, NotificationSettingOption.WEBAPP, NotificationSettingOption.PUSH);
        }
    }
}
