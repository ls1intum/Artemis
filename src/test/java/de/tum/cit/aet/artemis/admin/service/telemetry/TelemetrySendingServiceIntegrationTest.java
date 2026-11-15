package de.tum.cit.aet.artemis.admin.service.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * The sender and the build agent count it reads are lazy and only created by the deferred eager initialization, which test contexts do not run, so
 * this is the test that wires both from a real core, scheduling and local CI context.
 */
class TelemetrySendingServiceIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private TelemetrySendingService telemetrySendingService;

    @Test
    void buildsTheReportFromTheConfiguredInstallation() {
        var data = telemetrySendingService.buildTelemetryData(true, "startup-id", Instant.EPOCH);

        assertThat(data.operator()).isEqualTo("Artemis Test Operations");
        assertThat(data.adminName()).isEqualTo("Artemis Test Administrator");
        assertThat(data.universityName()).isEqualTo("Artemis Test University");
        assertThat(data.contact()).isEqualTo("test@localhost");
        assertThat(data.profiles()).contains("core", "scheduling", "localci");
        assertThat(data.moduleFeatures()).contains("iris");
        assertThat(data.buildAgentCount()).isNotNull().isNotNegative();
        assertThat(data.startupId()).isEqualTo("startup-id");
    }
}
