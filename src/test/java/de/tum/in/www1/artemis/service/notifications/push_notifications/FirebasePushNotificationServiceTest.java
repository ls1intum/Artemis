package de.tum.in.www1.artemis.service.notifications.push_notifications;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.NotificationConstants;
import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceConfiguration;
import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceType;
import de.tum.in.www1.artemis.repository.PushNotificationDeviceConfigurationRepository;

class FirebasePushNotificationServiceTest {

    @Mock
    private PushNotificationDeviceConfigurationRepository repositoryMock;

    @Mock
    private RestTemplate restTemplateMock;

    private FirebasePushNotificationService firebasePushNotificationService;

    private User student;

    private Notification notification;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        student = new User();
        student.setId(1L);
        student.setLogin("1");

        notification = new GroupNotification(null, NotificationConstants.NEW_ANNOUNCEMENT_POST_TITLE, NotificationConstants.NEW_ANNOUNCEMENT_POST_TEXT, false, new String[0],
                student, GroupNotificationType.STUDENT);

        PushNotificationDeviceConfiguration pushNotificationDeviceConfiguration = new PushNotificationDeviceConfiguration("test", PushNotificationDeviceType.FIREBASE, new Date(),
                HexFormat.of().parseHex("e04fd020ea3a6910a2d808002b30309d"), student);

        when(repositoryMock.findByUserIn(anyList(), eq(PushNotificationDeviceType.FIREBASE))).thenReturn(Collections.singletonList(pushNotificationDeviceConfiguration));

        firebasePushNotificationService = new FirebasePushNotificationService(repositoryMock, restTemplateMock);

        ReflectionTestUtils.setField(firebasePushNotificationService, "relayServerBaseUrl", Optional.of("test"));
    }

    @Test
    void sendNotificationRequestsToEndpoint_shouldSendNotifications() throws InterruptedException {
        // Given
        when(restTemplateMock.postForObject(any(String.class), any(HttpEntity.class), eq(String.class))).thenReturn("ok");

        // When
        firebasePushNotificationService.sendNotification(notification, student, null);
        sleep(1000);

        // Then
        verify(restTemplateMock, times(1)).postForObject(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void scheduleSendBatch_shouldRetryOnRestClientException() throws InterruptedException {
        // Given
        when(restTemplateMock.postForObject(anyString(), any(HttpEntity.class), any())).thenThrow(new RestClientException(""));

        // When
        firebasePushNotificationService.sendNotification(notification, student, null);
        sleep(5000);

        // Then
        verify(restTemplateMock, atLeast(3)).postForObject(anyString(), any(HttpEntity.class), any());
    }

    @Test
    void getDeviceType_shouldReturnFirebase() {
        // When
        PushNotificationDeviceType deviceType = firebasePushNotificationService.getDeviceType();

        // Then
        assertThat(deviceType).isEqualTo(PushNotificationDeviceType.FIREBASE);
    }

    @Test
    void getRepository_shouldReturnRepository() {
        // When
        PushNotificationDeviceConfigurationRepository repository = firebasePushNotificationService.getRepository();

        // Then
        assertThat(repository).isEqualTo(repositoryMock);
    }
}
