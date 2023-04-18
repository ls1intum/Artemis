package de.tum.in.www1.artemis.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceConfiguration;
import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceType;
import de.tum.in.www1.artemis.repository.PushNotificationDeviceConfigurationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.web.rest.push_notification.PushNotificationRegisterBody;
import de.tum.in.www1.artemis.web.rest.push_notification.PushNotificationRegisterDTO;
import de.tum.in.www1.artemis.web.rest.push_notification.PushNotificationUnregisterRequest;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PushNotificationResourceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    DatabaseUtilService databaseUtilService;

    @Autowired
    PushNotificationDeviceConfigurationRepository pushNotificationDeviceConfigurationRepository;

    private static final String fakeToken = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ4eDQ0eHh4IiwiYXV0aCI6IlJPTEVfVEEsUk9MRV9JTlNUUlVDVE9SLFJPTEVfVVNFUiIsImV4cCI6NDgzMjgzODk2M30.jTzuQGl1nwvkKfwRUa8DpoAotw0zXf9DuLo2-OPYFub7GyBheKHBRgsqtFKSdv5ISYuEFuPIWJCuQOA8cH5UWA";

    private static final String userLogin = "test-user";

    private static final String fakeFirebaseToken = "FakeFirebaseToken";

    private User user;

    @BeforeEach
    void setup() {
        user = databaseUtilService.createAndSaveUser(userLogin);
    }

    @AfterEach
    void teardown() {
        userRepository.delete(user);
        user = null;
    }

    @Test
    @WithMockUser(username = userLogin, roles = "USER", password = fakeToken)
    void testRegister() throws Exception {
        PushNotificationRegisterBody body = new PushNotificationRegisterBody(fakeFirebaseToken, PushNotificationDeviceType.FIREBASE);
        PushNotificationRegisterDTO response = request.postWithResponseBody("/api/push_notification/register", body, PushNotificationRegisterDTO.class);

        assertThat(response.secretKey()).isNotEmpty();
        List<PushNotificationDeviceConfiguration> deviceConfigurations = pushNotificationDeviceConfigurationRepository.findByUserIn(Collections.singletonList(user),
                PushNotificationDeviceType.FIREBASE);

        assertThat(deviceConfigurations).hasSize(1);
        PushNotificationDeviceConfiguration config = deviceConfigurations.get(0);
        assertThat(config.getDeviceType()).isEqualTo(PushNotificationDeviceType.FIREBASE);
        assertThat(config.getExpirationDate()).isInTheFuture();
    }

    @Test
    @WithMockUser(username = userLogin, roles = "USER", password = fakeToken)
    void testUnregister() throws Exception {
        testRegister();

        PushNotificationUnregisterRequest body = new PushNotificationUnregisterRequest(fakeFirebaseToken, PushNotificationDeviceType.FIREBASE);
        request.delete("/api/push_notification/unregister", HttpStatus.OK, body);

        List<PushNotificationDeviceConfiguration> deviceConfigurations = pushNotificationDeviceConfigurationRepository.findByUserIn(Collections.singletonList(user),
                PushNotificationDeviceType.FIREBASE);

        assertThat(deviceConfigurations).isEmpty();
    }

    @Test
    @WithMockUser(username = userLogin, roles = "USER")
    void testUnregisterNonExistentRegistration() throws Exception {
        PushNotificationUnregisterRequest body = new PushNotificationUnregisterRequest("Does not exist", PushNotificationDeviceType.FIREBASE);
        request.delete("/api/push_notification/unregister", HttpStatus.NOT_FOUND, body);
    }
}
