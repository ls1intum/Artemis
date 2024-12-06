package de.tum.cit.aet.artemis.programming.icl;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.context.annotation.Profile;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.programming.service.localvc.ssh.SshFingerprintsProviderService;
import de.tum.cit.aet.artemis.programming.web.localvc.ssh.SshFingerprintsProviderResource;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

@Profile(PROFILE_LOCALVC)
class SshFingerprintsProviderTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "sshFingerprintsTest";

    @Mock
    SshFingerprintsProviderService sshFingerprintsProviderService;

    SshFingerprintsProviderResource sshFingerprintsProviderResource;

    @BeforeEach
    void setUp() {
        sshFingerprintsProviderResource = new SshFingerprintsProviderResource(sshFingerprintsProviderService);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnFingerprints() throws Exception {
        Map<String, String> expectedFingerprints = new HashMap<>();
        expectedFingerprints.put("RSA", "key");
        doReturn(expectedFingerprints).when(sshFingerprintsProviderService).getSshFingerPrints();

        var response = sshFingerprintsProviderResource.getSshFingerprints().getBody();

        assertThat(response).isNotNull();
        assertThat(response.get("RSA")).isEqualTo("key");
    }
}
