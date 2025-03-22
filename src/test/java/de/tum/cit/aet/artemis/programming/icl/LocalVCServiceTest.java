package de.tum.cit.aet.artemis.programming.icl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;

class LocalVCServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "localvcservice";

    @Test
    void testHealth() {
        ConnectorHealth health = versionControlService.health();
        assertThat(health.additionalInfo().get("url")).isEqualTo(localVCBaseUrl);
    }
}
