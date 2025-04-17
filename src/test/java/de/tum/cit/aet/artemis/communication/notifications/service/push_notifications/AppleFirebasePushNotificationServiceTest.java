package de.tum.cit.aet.artemis.communication.notifications.service.push_notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.HexFormat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationApiType;
import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceConfiguration;
import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceType;
import de.tum.cit.aet.artemis.communication.repository.PushNotificationDeviceConfigurationRepository;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationPushProxyService;
import de.tum.cit.aet.artemis.communication.service.notifications.push_notifications.ApplePushNotificationService;
import de.tum.cit.aet.artemis.communication.service.notifications.push_notifications.FirebasePushNotificationService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;

class AppleFirebasePushNotificationServiceTest {

    @Mock
    private PushNotificationDeviceConfigurationRepository repositoryMock;

    @Mock
    private RestTemplate appleRestTemplateMock;

    @Mock
    private RestTemplate firebaseRestTemplateMock;

    @Mock
    private FeatureToggleService featureToggleService;

    private ApplePushNotificationService applePushNotificationService;

    private FirebasePushNotificationService firebasePushNotificationService;

    @Mock
    private CourseNotificationPushProxyService courseNotificationPushProxyService;

    private User student;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        student = new User();
        student.setId(1L);
        student.setLogin("1");

        String token = "test";
        byte[] payload = HexFormat.of().parseHex("e04fd020ea3a6910a2d808002b30309d");
        PushNotificationDeviceConfiguration applePushNotificationDeviceConfiguration = new PushNotificationDeviceConfiguration(token, PushNotificationDeviceType.APNS, new Date(),
                payload, student, PushNotificationApiType.IOS_V2, "1.0.0");
        PushNotificationDeviceConfiguration firebasePushNotificationDeviceConfiguration = new PushNotificationDeviceConfiguration(token, PushNotificationDeviceType.FIREBASE,
                new Date(), payload, student, PushNotificationApiType.DEFAULT, "1.0.0");

        when(repositoryMock.findByUserIn(anySet(), eq(PushNotificationDeviceType.APNS))).thenReturn(Collections.singletonList(applePushNotificationDeviceConfiguration));
        when(repositoryMock.findByUserIn(anySet(), eq(PushNotificationDeviceType.FIREBASE))).thenReturn(Collections.singletonList(firebasePushNotificationDeviceConfiguration));

        applePushNotificationService = new ApplePushNotificationService(courseNotificationPushProxyService, repositoryMock, appleRestTemplateMock);
        firebasePushNotificationService = new FirebasePushNotificationService(courseNotificationPushProxyService, repositoryMock, firebaseRestTemplateMock);

        ReflectionTestUtils.setField(applePushNotificationService, "relayServerBaseUrl", "test");
        ReflectionTestUtils.setField(firebasePushNotificationService, "relayServerBaseUrl", "test");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
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
