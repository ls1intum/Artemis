package de.tum.cit.aet.artemis.hyperion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerRegistryService;

class HyperionGenerationCapacityHealthIndicatorTest {

    private final GenerationWorkerRegistryService workers = mock();

    private final HyperionGenerationCapacityHealthIndicator indicator = new HyperionGenerationCapacityHealthIndicator(workers);

    @Test
    void reachableSupervisorWithoutQualifiedEngineReportsDown() {
        when(workers.reachableWorkers()).thenReturn(1);
        var health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reachableWorkers", 1).containsEntry("capableWorkers", 0).containsEntry("availableWorkers", 0);
    }

    @Test
    void busyCapableWorkerIsHealthyButDoesNotAdvertiseAdmissionCapacity() {
        when(workers.reachableWorkers()).thenReturn(1);
        when(workers.capableWorkers()).thenReturn(1);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails()).containsEntry("availableWorkers", 0);
        when(workers.availableWorkers()).thenReturn(1);
        assertThat(indicator.health().getDetails()).containsEntry("availableWorkers", 1);
    }
}
