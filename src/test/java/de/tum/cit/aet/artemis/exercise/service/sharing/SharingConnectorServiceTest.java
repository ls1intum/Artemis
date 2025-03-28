package de.tum.cit.aet.artemis.exercise.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class SharingConnectorServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Value("${artemis.sharing.apikey:#{null}}")
    private String sharingApiKey;

    @Autowired
    private SharingPlatformMockProvider sharingPlatformMockProvider;

    @Autowired
    private SharingConnectorService sharingConnectorService;

    @BeforeEach
    void startUp() throws Exception {
        sharingPlatformMockProvider.connectRequestFromSharingPlattform();
    }

    @AfterEach
    void tearDown() throws Exception {
        sharingPlatformMockProvider.reset();
    }

    @Test
    void trivialTests() {
        assertThat(sharingConnectorService.getInstallationName()).isEqualTo(SharingPlatformMockProvider.TEST_INSTALLATION_NAME);
        assertThat(sharingConnectorService.getSharingApiBaseUrlOrNull()).isNotNull();
        assertThat(sharingConnectorService.isSharingApiBaseUrlPresent()).isTrue();
        sharingConnectorService.setSharingApiBaseUrl(sharingConnectorService.getSharingApiBaseUrlOrNull());
        assertThat(sharingConnectorService.getSharingApiBaseUrlOrNull()).isNotNull();

        assertThat(sharingConnectorService.getSharingApiKeyOrNull()).isNotNull();
        sharingConnectorService.setSharingApiKey(sharingConnectorService.getSharingApiKeyOrNull());
        assertThat(sharingConnectorService.getSharingApiKeyOrNull()).isNotNull();
    }

    @Test
    void validateApiKey() {
        assertThat(sharingConnectorService.validate(null)).isFalse();
        String hugeKey = "huge" + "0123456789".repeat(50);

        assertThat(sharingConnectorService.validate(hugeKey)).isFalse();

        assertThat(sharingConnectorService.validate(sharingApiKey)).isTrue();
        assertThat(sharingConnectorService.validate("Bearer " + sharingApiKey)).isTrue();

        String fakeKey = "x1234123123sdfsdfxx";
        assertThat(sharingConnectorService.validate(fakeKey)).isFalse();
        assertThat(sharingConnectorService.validate("Bearer " + fakeKey)).isFalse();

    }

}
