package de.tum.in.www1.artemis.service.notifications.push_notifications;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.atLeast;

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

class ApplePushNotificationServiceTest {

    @Mock
    private PushNotificationDeviceConfigurationRepository repositoryMock;

    @Mock
    private RestTemplate restTemplateMock;

    private ApplePushNotificationService applePushNotificationService;

    private Notification notification;

    private User student;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        student = new User();
        student.setId(1L);
        student.setLogin("1");

        notification = new GroupNotification(null, NotificationConstants.NEW_ANNOUNCEMENT_POST_TITLE, NotificationConstants.NEW_ANNOUNCEMENT_POST_TEXT, false, new String[0],
                student, GroupNotificationType.STUDENT);

        PushNotificationDeviceConfiguration pushNotificationDeviceConfiguration = new PushNotificationDeviceConfiguration("test", PushNotificationDeviceType.APNS, new Date(),
                HexFormat.of().parseHex("e04fd020ea3a6910a2d808002b30309d"), student);

        when(repositoryMock.findByUserIn(anyList(), eq(PushNotificationDeviceType.APNS))).thenReturn(Collections.singletonList(pushNotificationDeviceConfiguration));

        applePushNotificationService = new ApplePushNotificationService(repositoryMock, restTemplateMock);

        ReflectionTestUtils.setField(applePushNotificationService, "relayServerBaseUrl", Optional.of("test"));
    }

    @Test
    public void sendNotificationRequestsToEndpoint_shouldSendNotifications() throws InterruptedException {
        // Given
        RelayNotificationRequest request = new RelayNotificationRequest("", "", "");

        when(restTemplateMock.postForObject(any(String.class), any(HttpEntity.class), eq(String.class))).thenReturn("ok");

        // When
        applePushNotificationService.sendNotification(notification, student, null);
        sleep(1000);

        // Then
        verify(restTemplateMock, times(1)).postForObject(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    public void scheduleSendBatch_shouldRetryOnRestClientException() throws InterruptedException {
        RelayNotificationRequest request = new RelayNotificationRequest("", "", "");

        when(restTemplateMock.postForObject(anyString(), any(HttpEntity.class), any())).thenThrow(new RestClientException(""));

        // When
        applePushNotificationService.sendNotification(notification, student, null);
        sleep(5000);

        // Then
        verify(restTemplateMock, atLeast(2)).postForObject(anyString(), any(HttpEntity.class), any());
    }

    @Test
    public void getDeviceType_shouldReturnAPNS() {
        // When
        PushNotificationDeviceType deviceType = applePushNotificationService.getDeviceType();

        // Then
        assertThat(deviceType).isEqualTo(PushNotificationDeviceType.APNS);
    }

    @Test
    public void getRepository_shouldReturnRepository() {
        // When
        PushNotificationDeviceConfigurationRepository repository = applePushNotificationService.getRepository();

        // Then
        assertThat(repository).isEqualTo(repositoryMock);
    }

}
