package de.tum.cit.aet.artemis.communication.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceConfiguration;
import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceType;
import de.tum.cit.aet.artemis.communication.dto.PushNotificationRegisterBody;
import de.tum.cit.aet.artemis.communication.dto.PushNotificationRegisterDTO;
import de.tum.cit.aet.artemis.communication.dto.PushNotificationUnregisterRequest;
import de.tum.cit.aet.artemis.communication.repository.PushNotificationDeviceConfigurationRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PushNotificationResourceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private PushNotificationDeviceConfigurationRepository pushNotificationDeviceConfigurationRepository;

    @Autowired
    private UserUtilService userUtilService;

    private static final String FAKE_TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ4eDQ0eHh4IiwiYXV0aCI6IlJPTEVfVEEsUk9MRV9JTlNUUlVDVE9SLFJPTEVfVVNFUiIsImV4cCI6MjUzNDAyMjU3NjAwfQ.mm9sUblgWLp97xC5ML2z6KZ0rQucKOyP7zmmI_bINlfu_axQ1dmw7A60gzOH7kzArWtx7ZmHYQZN3RMwlKHRIA";

    private static final String USER_LOGIN = "test-user";

    private static final String FAKE_FIREBASE_TOKEN = "FakeFirebaseToken";

    private User user;

    @BeforeEach
    void setup() {
        user = userUtilService.createAndSaveUser(USER_LOGIN);
    }

    @AfterEach
    void teardown() {
        userRepository.delete(user);
    }

    @Test
    @WithMockUser(username = USER_LOGIN, roles = "USER", password = FAKE_TOKEN)
    void testRegister() throws Exception {
        PushNotificationRegisterBody body = new PushNotificationRegisterBody(FAKE_FIREBASE_TOKEN, PushNotificationDeviceType.FIREBASE);
        PushNotificationRegisterDTO response = request.postWithResponseBody("/api/push_notification/register", body, PushNotificationRegisterDTO.class);

        assertThat(response.secretKey()).isNotEmpty();
        List<PushNotificationDeviceConfiguration> deviceConfigurations = pushNotificationDeviceConfigurationRepository.findByUserIn(Set.of(user),
                PushNotificationDeviceType.FIREBASE);

        assertThat(deviceConfigurations).hasSize(1);
        PushNotificationDeviceConfiguration config = deviceConfigurations.getFirst();
        assertThat(config.getDeviceType()).isEqualTo(PushNotificationDeviceType.FIREBASE);
        assertThat(config.getExpirationDate()).isInTheFuture();
    }

    @Test
    @WithMockUser(username = USER_LOGIN, roles = "USER", password = FAKE_TOKEN)
    void testUnregister() throws Exception {
        testRegister();

        PushNotificationUnregisterRequest body = new PushNotificationUnregisterRequest(FAKE_FIREBASE_TOKEN, PushNotificationDeviceType.FIREBASE);
        request.delete("/api/push_notification/unregister", HttpStatus.OK, body);

        List<PushNotificationDeviceConfiguration> deviceConfigurations = pushNotificationDeviceConfigurationRepository.findByUserIn(Set.of(user),
                PushNotificationDeviceType.FIREBASE);

        assertThat(deviceConfigurations).isEmpty();
    }

    @Test
    @WithMockUser(username = USER_LOGIN, roles = "USER")
    void testUnregisterNonExistentRegistration() throws Exception {
        PushNotificationUnregisterRequest body = new PushNotificationUnregisterRequest("Does not exist", PushNotificationDeviceType.FIREBASE);
        request.delete("/api/push_notification/unregister", HttpStatus.NOT_FOUND, body);
    }
}
