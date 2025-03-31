package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.communication.domain.CourseNotification;
import de.tum.cit.aet.artemis.communication.domain.CourseNotificationParameter;
import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationStatus;
import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationStatusType;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.CourseNotificationCategory;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationPageableDTO;
import de.tum.cit.aet.artemis.communication.dto.CourseNotificationWithStatusDTO;
import de.tum.cit.aet.artemis.communication.test_repository.CourseNotificationParameterTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.CourseNotificationTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;

@ExtendWith(MockitoExtension.class)
class CourseNotificationServiceTest {

    private CourseNotificationService courseNotificationService;

    @Mock
    private CourseNotificationRegistryService courseNotificationRegistryService;

    @Mock
    private CourseNotificationSettingService courseNotificationSettingService;

    @Mock
    private CourseNotificationTestRepository courseNotificationRepository;

    @Mock
    private CourseNotificationParameterTestRepository courseNotificationParameterRepository;

    @Mock
    private UserCourseNotificationStatusService userCourseNotificationStatusService;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private CourseNotificationWebappService webappService;

    @Mock
    private CourseNotificationPushService pushService;

    @Mock
    private CourseNotificationEmailService emailService;

    @BeforeEach
    void setUp() {
        courseNotificationService = new CourseNotificationService(courseNotificationRegistryService, courseNotificationSettingService, courseNotificationRepository,
                courseNotificationParameterRepository, userCourseNotificationStatusService, userRepository, webappService, pushService, emailService);
    }

    @Test
    void shouldSendNotificationsToAllChannelsWhenMultipleChannelsSupported() {
        TestNotification notification = createTestNotification(NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH);
        List<User> allRecipients = List.of(createTestUser(1L), createTestUser(2L));
        List<User> webappRecipients = List.of(createTestUser(1L));
        List<User> pushRecipients = List.of(createTestUser(2L));

        when(courseNotificationSettingService.filterRecipientsBy(notification, allRecipients, NotificationChannelOption.WEBAPP)).thenReturn(webappRecipients);
        when(courseNotificationSettingService.filterRecipientsBy(notification, allRecipients, NotificationChannelOption.PUSH)).thenReturn(pushRecipients);
        when(courseNotificationRepository.save(any())).thenReturn(createTestCourseNotificationEntity(1L));
        when(courseNotificationRegistryService.getNotificationIdentifier(any())).thenReturn((short) 1);

        courseNotificationService.sendCourseNotification(notification, allRecipients);

        verify(webappService).sendCourseNotification(any(CourseNotificationDTO.class), eq(webappRecipients));
        verify(pushService).sendCourseNotification(any(CourseNotificationDTO.class), eq(pushRecipients));
        verify(emailService, never()).sendCourseNotification(any(CourseNotificationDTO.class), anyList());

        ArgumentCaptor<HashSet<User>> notifiedUsersCaptor = ArgumentCaptor.forClass(HashSet.class);
        verify(userCourseNotificationStatusService).batchCreateStatusForUsers(notifiedUsersCaptor.capture(), eq(1L), eq(123L));
        assertThat(notifiedUsersCaptor.getValue()).containsExactlyInAnyOrder(createTestUser(1L), createTestUser(2L));
    }

    @Test
    void shouldCreateCourseNotificationWhenSending() {
        TestNotification notification = createTestNotification(NotificationChannelOption.WEBAPP);
        List<User> recipients = List.of(createTestUser(1L));

        when(courseNotificationSettingService.filterRecipientsBy(any(), any(), any())).thenReturn(recipients);
        when(courseNotificationRepository.save(any())).thenReturn(createTestCourseNotificationEntity(1L));
        when(courseNotificationRegistryService.getNotificationIdentifier(any())).thenReturn((short) 1);

        courseNotificationService.sendCourseNotification(notification, recipients);

        ArgumentCaptor<CourseNotification> entityCaptor = ArgumentCaptor.forClass(CourseNotification.class);
        verify(courseNotificationRepository).save(entityCaptor.capture());

        CourseNotification savedEntity = entityCaptor.getValue();
        assertThat(savedEntity.getCourse().getId()).isEqualTo(123L);
        assertThat(savedEntity.getType()).isEqualTo((short) 1);
    }

    @Test
    void shouldReturnCourseNotificationsWhenRequested() {
        long courseId = 123L;
        long userId = 1L;
        Pageable pageable = Pageable.unpaged();

        CourseNotification entity = createTestCourseNotificationEntity(1L);
        CourseNotificationParameter param1 = new CourseNotificationParameter(entity, "key1", "value1");
        CourseNotificationParameter param2 = new CourseNotificationParameter(entity, "key2", "value2");
        entity.setParameters(Set.of(param1, param2));
        UserCourseNotificationStatus status = new UserCourseNotificationStatus();
        status.setCourseNotification(entity);
        status.setStatus(UserCourseNotificationStatusType.SEEN);

        PageImpl<CourseNotificationWithStatusDTO> page = new PageImpl<>(List.of(new CourseNotificationWithStatusDTO(entity, status)));

        when(courseNotificationRepository.findCourseNotificationsByUserIdAndCourseIdAndStatusNotArchived(userId, courseId, pageable)).thenReturn(page);
        when(courseNotificationRegistryService.getNotificationClass(any())).thenReturn((Class) TestNotification.class);

        CourseNotificationPageableDTO<CourseNotificationDTO> result = courseNotificationService.getCourseNotifications(pageable, courseId, userId);

        assertThat(result).isNotNull();
        assertThat(result.content()).hasSize(1);

        var dto = result.content().getFirst();
        assertThat(dto.notificationType()).isEqualTo("testNotification");
        assertThat(dto.courseId()).isEqualTo(123L);
    }

    @Test
    void shouldConvertParametersToMapWhenProcessingNotification() {
        CourseNotification entity = createTestCourseNotificationEntity(1L);
        CourseNotificationParameter param1 = new CourseNotificationParameter(entity, "key1", "value1");
        CourseNotificationParameter param2 = new CourseNotificationParameter(entity, "key2", "value2");
        Set<CourseNotificationParameter> paramSet = Set.of(param1, param2);

        Map<String, String> result = ReflectionTestUtils.invokeMethod(courseNotificationService, "parametersToMap", paramSet);

        assertThat(result).hasSize(2);
        assertThat(result).containsEntry("key1", "value1");
        assertThat(result).containsEntry("key2", "value2");
    }

    private User createTestUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setLogin("user" + id);
        return user;
    }

    private TestNotification createTestNotification(NotificationChannelOption... supportedChannels) {
        return new TestNotification(1L, 123L, ZonedDateTime.now(), new HashMap<String, String>(Map.of("key1", "val1", "key2", "val2")), supportedChannels);
    }

    private CourseNotification createTestCourseNotificationEntity(Long id) {
        Course course = new Course();
        course.setId(123L);

        CourseNotification entity = new CourseNotification();
        entity.setId(id);
        entity.setCourse(course);
        entity.setType((short) 1);
        entity.setCreationDate(ZonedDateTime.now());
        entity.setDeletionDate(ZonedDateTime.now().plusDays(7));

        return entity;
    }

    static class TestNotification extends de.tum.cit.aet.artemis.communication.domain.course_notifications.CourseNotification {

        final Set<NotificationChannelOption> supportedChannels;

        // This constructor is needed for the test case shouldReturnCourseNotificationsWhenRequested
        TestNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
            super(notificationId, courseId, creationDate, parameters);
            this.supportedChannels = Set.of(NotificationChannelOption.WEBAPP);
        }

        TestNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters, NotificationChannelOption... supportedChannels) {
            super(notificationId, courseId, creationDate, parameters);
            this.supportedChannels = new HashSet<>(Arrays.asList(supportedChannels));
        }

        @Override
        public String getReadableNotificationType() {
            return "testNotification";
        }

        @Override
        public CourseNotificationCategory getCourseNotificationCategory() {
            return CourseNotificationCategory.GENERAL;
        }

        @Override
        public List<NotificationChannelOption> getSupportedChannels() {
            return supportedChannels.stream().toList();
        }

        @Override
        public String getRelativeWebAppUrl() {
            return "/";
        }

        @Override
        public Duration getCleanupDuration() {
            return Duration.ofDays(30);
        }
    }
}
