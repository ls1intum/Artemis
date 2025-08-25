package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class SharingConnectorServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private SharingPlatformMockProvider sharingPlatformMockProvider;

    @Autowired
    private SharingConnectorService sharingConnectorService;

    @BeforeEach
    void startUp() throws Exception {
        sharingPlatformMockProvider.connectRequestFromSharingPlatform();
    }

    @AfterEach
    void tearDown() throws Exception {
        sharingPlatformMockProvider.reset();
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

        assertThat(sharingConnectorService.getSharingApiKeyOrNull()).isNotNull();
        sharingConnectorService.setSharingApiKey(sharingConnectorService.getSharingApiKeyOrNull());
        assertThat(sharingConnectorService.getSharingApiKeyOrNull()).isNotNull();
    }

    @Test
    void validateApiKey_withNullKey_shouldReturnFalse() {
        assertThat(sharingConnectorService.validateApiKey(null)).isFalse();
    }

    @Test
    void validateApiKey_withHugeKey_shouldReturnFalse() {
        /*
         * just a huge key to trigger a validation error
         */
        String hugeKey = "huge" + "0123456789".repeat(50);

        assertThat(sharingConnectorService.validateApiKey(hugeKey)).isFalse();
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

}
