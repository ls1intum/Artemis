package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class SharingConnectorServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private SharingPlatformMockProvider sharingPlatformMockProvider;

    @Autowired
    @Qualifier("sharingRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    private SharingConnectorService sharingConnectorService;

    @BeforeEach
    void startUp() {
        try {
            sharingPlatformMockProvider.connectRequestFromSharingPlatform();
        }
        catch (Exception e) {
            fail(e);
        }
    }

    @AfterEach
    void tearDown() {
        try {
            sharingPlatformMockProvider.reset();
        }
        catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void testConfigurationProperties() {
        assertThat(sharingConnectorService.getInstallationName()).isEqualTo(SharingPlatformMockProvider.TEST_INSTALLATION_NAME);
        assertThat(sharingConnectorService.getSharingApiBaseUrlOrNull()).isNotNull();
        assertThat(sharingConnectorService.isSharingApiBaseUrlPresent()).isTrue();
    }

    @Test
    void testApiKeySetterAndGetter() {
        sharingConnectorService.setSharingApiBaseUrl(sharingConnectorService.getSharingApiBaseUrlOrNull());
        assertThat(sharingConnectorService.getSharingApiBaseUrlOrNull()).isNotNull();

        assertThat(sharingConnectorService.getSharingApiKey()).isNotNull();
        sharingConnectorService.setSharingApiKey(sharingConnectorService.getSharingApiKey());
        assertThat(sharingConnectorService.getSharingApiKey()).isNotNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\t", "\n" })
    void validateApiKey_withNullOrBlankKey_shouldReturnFalse(String key) {
        assertThat(sharingConnectorService.validateApiKey(key)).isFalse();
    }

    @Test
    void validateApiKey_withHugeKey_shouldReturnFalse() {
        /*
         * just a huge key to trigger a validation error
         */
        String hugeKey = "huge" + "0123456789".repeat(50);

        assertThat(sharingConnectorService.validateApiKey(hugeKey)).isFalse();
        assertThat(sharingConnectorService.validateApiKey(hugeKey.substring(0, SharingConnectorService.MAX_API_KEY_LENGTH + 1))).isFalse();
        String admissibleKey = hugeKey.substring(0, SharingConnectorService.MAX_API_KEY_LENGTH);
        String configuredApiKey = sharingConnectorService.getSharingApiKey();
        try {
            sharingConnectorService.setSharingApiKey(admissibleKey);
            assertThat(sharingConnectorService.validateApiKey(admissibleKey)).isTrue();
        }
        finally {
            sharingConnectorService.setSharingApiKey(configuredApiKey);
        }
    }

    @Test
    void validateApiKey_withValidKey_shouldReturnTrue() {
        assertThat(sharingConnectorService.validateApiKey(sharingPlatformMockProvider.getTestSharingApiKey())).isTrue();
        assertThat(sharingConnectorService.validateApiKey("Bearer " + sharingPlatformMockProvider.getTestSharingApiKey())).isTrue();
    }

    @Test
    void validateApiKey_withFakeKey_shouldReturnFalse() {
        String fakeKey = "x1234123123sdfsdfxx";
        assertThat(sharingConnectorService.validateApiKey(fakeKey)).isFalse();
        assertThat(sharingConnectorService.validateApiKey("Bearer " + fakeKey)).isFalse();
    }

    @Test
    void testTriggerReinit() {
        String reinitUrl = SharingPlatformMockProvider.SHARING_BASEURL_PLUGIN + "/reInitialize?apiKey=" + sharingPlatformMockProvider.getTestSharingApiKey();
        final ResponseActions responseActions = sharingPlatformMockProvider.getMockSharingServer().expect(ExpectedCount.once(), requestTo(reinitUrl))
                .andExpect(method(HttpMethod.GET));
        responseActions.andRespond(MockRestResponseCreators.withSuccess("true", MediaType.APPLICATION_JSON));
        sharingConnectorService.triggerReinit();
        SharingConnectorService.HealthStatusWithHistory lastHealthStati = sharingConnectorService.getLastHealthStati();
        assertThat(lastHealthStati).isNotEmpty();
        assertThat(lastHealthStati.getLast().getStatusMessage()).describedAs("This assertion may fail due to race conditions!")
                .isEqualTo("Requested reinitialization from Sharing Platform via http://localhost:9001");
    }

}
