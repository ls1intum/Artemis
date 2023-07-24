package de.tum.in.www1.artemis.service.notifications.push_notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

class AppleFirebasePushNotificationServiceTest {

    @Mock
    private PushNotificationDeviceConfigurationRepository repositoryMock;

    @Mock
    private RestTemplate appleRestTemplateMock;

    @Mock
    private RestTemplate firebaseRestTemplateMock;

    private ApplePushNotificationService applePushNotificationService;

    private FirebasePushNotificationService firebasePushNotificationService;

    private Notification notification;

    private User student;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        student = new User();
        student.setId(1L);
        student.setLogin("1");

        notification = new GroupNotification(null, NotificationConstants.NEW_ANNOUNCEMENT_POST_TITLE, NotificationConstants.NEW_ANNOUNCEMENT_POST_TEXT, false, new String[0],
                student, GroupNotificationType.STUDENT);

        String token = "test";
        byte[] payload = HexFormat.of().parseHex("e04fd020ea3a6910a2d808002b30309d");
        PushNotificationDeviceConfiguration applePushNotificationDeviceConfiguration = new PushNotificationDeviceConfiguration(token, PushNotificationDeviceType.APNS, new Date(),
                payload, student);
        PushNotificationDeviceConfiguration firebasePushNotificationDeviceConfiguration = new PushNotificationDeviceConfiguration(token, PushNotificationDeviceType.FIREBASE,
                new Date(), payload, student);

        when(repositoryMock.findByUserIn(anySet(), eq(PushNotificationDeviceType.APNS))).thenReturn(Collections.singletonList(applePushNotificationDeviceConfiguration));
        when(repositoryMock.findByUserIn(anySet(), eq(PushNotificationDeviceType.FIREBASE))).thenReturn(Collections.singletonList(firebasePushNotificationDeviceConfiguration));

        applePushNotificationService = new ApplePushNotificationService(repositoryMock, appleRestTemplateMock);
        firebasePushNotificationService = new FirebasePushNotificationService(repositoryMock, firebaseRestTemplateMock);

        ReflectionTestUtils.setField(applePushNotificationService, "relayServerBaseUrl", Optional.of("test"));
        ReflectionTestUtils.setField(firebasePushNotificationService, "relayServerBaseUrl", Optional.of("test"));
    }

    @Test
    void sendNotificationRequestsToEndpoint_shouldSendNotifications() throws InterruptedException {
        // Given
        when(appleRestTemplateMock.postForObject(any(String.class), any(HttpEntity.class), eq(String.class))).thenReturn("ok");
        when(firebaseRestTemplateMock.postForObject(any(String.class), any(HttpEntity.class), eq(String.class))).thenReturn("ok");

        // When
        applePushNotificationService.sendNotification(notification, student, null);
        firebasePushNotificationService.sendNotification(notification, student, null);

        // Then
        verify(appleRestTemplateMock, timeout(1000)).postForObject(anyString(), any(HttpEntity.class), eq(String.class));
        verify(firebaseRestTemplateMock, timeout(1000)).postForObject(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void scheduleSendBatch_shouldRetryOnRestClientException() throws InterruptedException {
        when(appleRestTemplateMock.postForObject(anyString(), any(HttpEntity.class), any())).thenThrow(new RestClientException(""));
        when(firebaseRestTemplateMock.postForObject(anyString(), any(HttpEntity.class), any())).thenThrow(new RestClientException(""));

        // When
        applePushNotificationService.sendNotification(notification, student, null);
        firebasePushNotificationService.sendNotification(notification, student, null);

        // Then
        verify(appleRestTemplateMock, timeout(5000).atLeast(2)).postForObject(anyString(), any(HttpEntity.class), any());
        verify(firebaseRestTemplateMock, timeout(5000).atLeast(2)).postForObject(anyString(), any(HttpEntity.class), any());
    }

    @Test
    void getDeviceType_shouldReturnAPNS() {
        // When
        PushNotificationDeviceType deviceType = applePushNotificationService.getDeviceType();

        // Then
        assertThat(deviceType).isEqualTo(PushNotificationDeviceType.APNS);
    }

    @Test
    void getRepository_shouldReturnRepository() {
        // When
        PushNotificationDeviceConfigurationRepository repository = applePushNotificationService.getRepository();

        // Then
        assertThat(repository).isEqualTo(repositoryMock);
    }

    @Test
    void getDeviceType_shouldReturnFirebase() {
        // When
        PushNotificationDeviceType deviceType = firebasePushNotificationService.getDeviceType();

        // Then
        assertThat(deviceType).isEqualTo(PushNotificationDeviceType.FIREBASE);
    }
}
