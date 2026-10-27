package de.tum.cit.aet.artemis.notification.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.notification.domain.push_notification.PushNotificationApiType;
import de.tum.cit.aet.artemis.notification.domain.push_notification.PushNotificationDeviceConfiguration;
import de.tum.cit.aet.artemis.notification.domain.push_notification.PushNotificationDeviceConfigurationId;
import de.tum.cit.aet.artemis.notification.domain.push_notification.PushNotificationDeviceType;
import de.tum.cit.aet.artemis.notification.dto.PushNotificationRegisterBodyDTO;
import de.tum.cit.aet.artemis.notification.dto.PushNotificationRegisterDTO;
import de.tum.cit.aet.artemis.notification.dto.PushNotificationUnregisterRequestDTO;
import de.tum.cit.aet.artemis.notification.repository.PushNotificationDeviceConfigurationRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class PushNotificationResourceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private JsonMapper objectMapper;

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

    private User otherUser;

    @BeforeEach
    void setup() {
        // make sure the configurations are empty to avoid flaky tests
        pushNotificationDeviceConfigurationRepository.deleteAll();
        user = userUtilService.createAndSaveUser(USER_LOGIN);
    }

    @AfterEach
    void teardown() {
        // Delete configurations before user to respect FK constraints
        pushNotificationDeviceConfigurationRepository.deleteAll();
        userRepository.delete(user);
        if (otherUser != null) {
            userRepository.delete(otherUser);
        }
    }

    @Test
    @WithMockUser(username = USER_LOGIN, roles = "USER", password = FAKE_TOKEN)
    void shouldRegisterTokenWhenCredentialsAreValid() throws Exception {
        PushNotificationRegisterBodyDTO body = new PushNotificationRegisterBodyDTO(FAKE_FIREBASE_TOKEN, PushNotificationDeviceType.FIREBASE);
        PushNotificationRegisterDTO response = request.postWithResponseBody("/api/notification/push_notification/register", body, PushNotificationRegisterDTO.class);
        assertThat(response.secretKey()).isNotEmpty();
        assertThat(response.algorithm()).isEqualTo(Constants.PUSH_NOTIFICATION_ENCRYPTION_ALGORITHM);

        var deviceConfigurations = pushNotificationDeviceConfigurationRepository.findByUserIdIn(Set.of(user.getId()), PushNotificationDeviceType.FIREBASE);
        assertThat(deviceConfigurations).hasSize(1);
        PushNotificationDeviceConfiguration config = deviceConfigurations.getFirst();
        assertThat(config.getDeviceType()).isEqualTo(PushNotificationDeviceType.FIREBASE);
        assertThat(config.getExpirationDate()).isInTheFuture();
        assertThat(config.getVersionCode()).isNull();
        assertThat(config.getApiType()).isEqualTo(PushNotificationApiType.DEFAULT);
        assertThat(config.getOwner().getId()).isEqualTo(user.getId());
        assertThat(Base64.getDecoder().decode(response.secretKey())).hasSize(32).isEqualTo(config.getSecretKey());
    }

    @Test
    @WithMockUser(username = USER_LOGIN, roles = "USER", password = FAKE_TOKEN)
    void shouldRegisterVersionCodeWhenSupplied() throws Exception {
        PushNotificationRegisterBodyDTO body = new PushNotificationRegisterBodyDTO(FAKE_FIREBASE_TOKEN, PushNotificationDeviceType.FIREBASE, PushNotificationApiType.DEFAULT,
                "1.1.1");
        PushNotificationRegisterDTO response = request.postWithResponseBody("/api/notification/push_notification/register", body, PushNotificationRegisterDTO.class);
        assertThat(response.secretKey()).isNotEmpty();
        assertThat(response.algorithm()).isEqualTo(Constants.PUSH_NOTIFICATION_ENCRYPTION_ALGORITHM);

        var deviceConfigurations = pushNotificationDeviceConfigurationRepository.findByUserIdIn(Set.of(user.getId()), PushNotificationDeviceType.FIREBASE);
        assertThat(deviceConfigurations).hasSize(1);
        PushNotificationDeviceConfiguration config = deviceConfigurations.getFirst();
        assertThat(config.getDeviceType()).isEqualTo(PushNotificationDeviceType.FIREBASE);
        assertThat(config.getExpirationDate()).isInTheFuture();
        assertThat(config.getVersionCode()).isEqualTo("1.1.1");
    }

    @Test
    @WithMockUser(username = USER_LOGIN, roles = "USER", password = FAKE_TOKEN)
    void shouldNotRegisterWhenWrongFormatOfVersionCodeIsSupplied() throws Exception {
        PushNotificationRegisterBodyDTO body = new PushNotificationRegisterBodyDTO(FAKE_FIREBASE_TOKEN, PushNotificationDeviceType.FIREBASE, PushNotificationApiType.DEFAULT,
                "asdf");
        request.performMvcRequest(
                MockMvcRequestBuilders.post("/api/notification/push_notification/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.status").value(500)).andExpect(jsonPath("$.message").value("error.http.500"));
        var deviceConfigurations = pushNotificationDeviceConfigurationRepository.findByUserIdIn(Set.of(user.getId()), PushNotificationDeviceType.FIREBASE);
        assertThat(deviceConfigurations).hasSize(0);
    }

    @Test
    @WithMockUser(username = USER_LOGIN, roles = "USER", password = FAKE_TOKEN)
    void shouldUnregisterWhenRequestingWithValidToken() throws Exception {
        shouldRegisterTokenWhenCredentialsAreValid();

        var params = new LinkedMultiValueMap<String, String>();
        params.add("token", FAKE_FIREBASE_TOKEN);
        params.add("deviceType", PushNotificationDeviceType.FIREBASE.name());
        request.delete("/api/notification/push_notification/unregister", HttpStatus.OK, params);
        var deviceConfigurations = pushNotificationDeviceConfigurationRepository.findByUserIdIn(Set.of(user.getId()), PushNotificationDeviceType.FIREBASE);
        assertThat(deviceConfigurations).isEmpty();
    }

    @Test
    @WithMockUser(username = USER_LOGIN, roles = "USER")
    void testUnregisterNonExistentRegistration() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("token", "Does not exist");
        params.add("deviceType", PushNotificationDeviceType.FIREBASE.name());
        request.delete("/api/notification/push_notification/unregister", HttpStatus.NOT_FOUND, params);
    }

    @Test
    @WithMockUser(username = USER_LOGIN, roles = "USER")
    void shouldRejectBlankTokenAsBadRequest() throws Exception {
        // A blank token (e.g. "...?token=&deviceType=FIREBASE") must be rejected rather than treated as a real token.
        var params = new LinkedMultiValueMap<String, String>();
        params.add("token", "");
        params.add("deviceType", PushNotificationDeviceType.FIREBASE.name());
        request.delete("/api/notification/push_notification/unregister", HttpStatus.BAD_REQUEST, params);
    }

    @Test
    @WithMockUser(username = USER_LOGIN, roles = "USER", password = FAKE_TOKEN)
    void shouldUnregisterWhenUsingDeprecatedRequestBody() throws Exception {
        shouldRegisterTokenWhenCredentialsAreValid();

        // Backwards compatibility: older (mobile) clients still pass the token and device type in the request body.
        PushNotificationUnregisterRequestDTO body = new PushNotificationUnregisterRequestDTO(FAKE_FIREBASE_TOKEN, PushNotificationDeviceType.FIREBASE);
        request.delete("/api/notification/push_notification/unregister", HttpStatus.OK, body);
        var deviceConfigurations = pushNotificationDeviceConfigurationRepository.findByUserIdIn(Set.of(user.getId()), PushNotificationDeviceType.FIREBASE);
        assertThat(deviceConfigurations).isEmpty();
    }

    @Test
    @WithMockUser(username = USER_LOGIN, roles = "USER", password = FAKE_TOKEN)
    void shouldDefaultOmittedAndExplicitNullApiAndVersionWithoutChangingStoredIdentity() throws Exception {
        for (String optionalFields : new String[] { "", ",\"apiType\":null,\"versionCode\":null" }) {
            request.performMvcRequest(MockMvcRequestBuilders.post("/api/notification/push_notification/register").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"fake-default-token\",\"deviceType\":\"FIREBASE\"" + optionalFields + "}")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.secretKey").isString()).andExpect(jsonPath("$.algorithm").value(Constants.PUSH_NOTIFICATION_ENCRYPTION_ALGORITHM));
            var configs = pushNotificationDeviceConfigurationRepository.findByUserIdIn(Set.of(user.getId()), PushNotificationDeviceType.FIREBASE);
            assertThat(configs).hasSize(1);
            assertThat(configs.getFirst().getApiType()).isEqualTo(PushNotificationApiType.DEFAULT);
            assertThat(configs.getFirst().getVersionCode()).isNull();
        }
    }

    @Test
    @WithMockUser(username = USER_LOGIN, roles = "USER", password = FAKE_TOKEN)
    void shouldRetainApnsIosV2AndDottedVersion() throws Exception {
        var input = new PushNotificationRegisterBodyDTO("fake-apns-token", PushNotificationDeviceType.APNS, PushNotificationApiType.IOS_V2, "12.34.567");
        var result = request.postWithResponseBody("/api/notification/push_notification/register", input, PushNotificationRegisterDTO.class);
        var configs = pushNotificationDeviceConfigurationRepository.findByUserIdIn(Set.of(user.getId()), PushNotificationDeviceType.APNS);
        assertThat(configs).hasSize(1);
        assertThat(configs.getFirst().getApiType()).isEqualTo(PushNotificationApiType.IOS_V2);
        assertThat(configs.getFirst().getVersionCode()).isEqualTo("12.34.567");
        assertThat(Base64.getDecoder().decode(result.secretKey())).hasSize(32).isEqualTo(configs.getFirst().getSecretKey());
    }

    @Test
    @WithMockUser(username = USER_LOGIN, roles = "USER", password = "fake-invalid-credential")
    void shouldReturnUnauthorizedAndNotPersistWhenCredentialsCannotBeParsed() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.post("/api/notification/push_notification/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"fake-device-token\",\"deviceType\":\"FIREBASE\"}")).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        assertThat(pushNotificationDeviceConfigurationRepository.count()).isZero();
    }

    private static Stream<Arguments> unregisterSources() {
        return Stream.of(
                Arguments.of("query-fake", PushNotificationDeviceType.FIREBASE, "body-fake", PushNotificationDeviceType.APNS, "query-fake", PushNotificationDeviceType.FIREBASE),
                Arguments.of("query-fake", null, "body-fake", PushNotificationDeviceType.APNS, "query-fake", PushNotificationDeviceType.APNS),
                Arguments.of(null, PushNotificationDeviceType.APNS, "body-fake", PushNotificationDeviceType.FIREBASE, "body-fake", PushNotificationDeviceType.APNS),
                Arguments.of("  ", PushNotificationDeviceType.APNS, "body-fake", PushNotificationDeviceType.FIREBASE, "body-fake", PushNotificationDeviceType.APNS));
    }

    @ParameterizedTest
    @MethodSource("unregisterSources")
    @WithMockUser(username = USER_LOGIN, roles = "USER")
    void shouldResolveEachQueryFieldIndependentlyBeforeLegacyBody(String queryToken, PushNotificationDeviceType queryType, String bodyToken, PushNotificationDeviceType bodyType,
            String removedToken, PushNotificationDeviceType removedType) throws Exception {
        seedRegistration(user, removedToken, removedType);
        seedRegistration(user, bodyToken, bodyType);
        var builder = MockMvcRequestBuilders.delete("/api/notification/push_notification/unregister").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PushNotificationUnregisterRequestDTO(bodyToken, bodyType)));
        if (queryToken != null) {
            builder.queryParam("token", queryToken);
        }
        if (queryType != null) {
            builder.queryParam("deviceType", queryType.name());
        }
        request.performMvcRequest(builder).andExpect(status().isOk()).andExpect(content().string(""));
        assertThat(pushNotificationDeviceConfigurationRepository.existsById(new PushNotificationDeviceConfigurationId(user, removedToken, removedType))).isFalse();
        assertThat(pushNotificationDeviceConfigurationRepository.existsById(new PushNotificationDeviceConfigurationId(user, bodyToken, bodyType))).isTrue();
    }

    @Test
    @WithMockUser(username = USER_LOGIN, roles = "USER")
    void shouldKeepAnotherUsersIdenticalTokenAndDeviceRegistration() throws Exception {
        otherUser = userUtilService.createAndSaveUser("push-contract-other-user");
        String fakeToken = "fake +/ = device π";
        seedRegistration(otherUser, fakeToken, PushNotificationDeviceType.APNS);
        var ownId = new PushNotificationDeviceConfigurationId(user, fakeToken, PushNotificationDeviceType.APNS);
        var otherId = new PushNotificationDeviceConfigurationId(otherUser, fakeToken, PushNotificationDeviceType.APNS);
        request.performMvcRequest(MockMvcRequestBuilders.delete("/api/notification/push_notification/unregister").queryParam("token", fakeToken).queryParam("deviceType", "APNS"))
                .andExpect(status().isNotFound()).andExpect(content().string(""));
        assertThat(pushNotificationDeviceConfigurationRepository.existsById(otherId)).isTrue();
        seedRegistration(user, fakeToken, PushNotificationDeviceType.APNS);
        request.performMvcRequest(MockMvcRequestBuilders.delete("/api/notification/push_notification/unregister").queryParam("token", fakeToken).queryParam("deviceType", "APNS"))
                .andExpect(status().isOk()).andExpect(content().string(""));
        assertThat(pushNotificationDeviceConfigurationRepository.existsById(ownId)).isFalse();
        assertThat(pushNotificationDeviceConfigurationRepository.existsById(otherId)).isTrue();
    }

    @Test
    @WithMockUser(username = USER_LOGIN, roles = "USER")
    void shouldRejectIncompleteAndInvalidEffectiveUnregistrationFields() throws Exception {
        for (String body : new String[] { "{}", "{\"token\":\" \",\"deviceType\":\"FIREBASE\"}", "{\"token\":\"fake\"}", "{\"deviceType\":\"APNS\"}",
                "{\"token\":\"fake\",\"deviceType\":\"UNKNOWN\"}" }) {
            request.performMvcRequest(MockMvcRequestBuilders.delete("/api/notification/push_notification/unregister").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        request.performMvcRequest(MockMvcRequestBuilders.delete("/api/notification/push_notification/unregister")).andExpect(status().isBadRequest());
        request.performMvcRequest(MockMvcRequestBuilders.delete("/api/notification/push_notification/unregister").queryParam("token", "fake").queryParam("deviceType", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        assertThat(pushNotificationDeviceConfigurationRepository.count()).isZero();
    }

    private void seedRegistration(User owner, String token, PushNotificationDeviceType deviceType) {
        pushNotificationDeviceConfigurationRepository.save(new PushNotificationDeviceConfiguration(token, deviceType, Date.from(Instant.now().plus(1, ChronoUnit.DAYS)),
                new byte[32], userRepository.getReferenceById(owner.getId()), PushNotificationApiType.DEFAULT, null));
    }

}
