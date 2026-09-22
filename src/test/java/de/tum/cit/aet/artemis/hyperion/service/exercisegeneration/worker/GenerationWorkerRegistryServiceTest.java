package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.tum.cit.aet.artemis.aiworker.api.AiWorkerApi;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerState;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerStatusDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerMessageCodec;

class GenerationWorkerRegistryServiceTest {

    @ParameterizedTest
    @CsvSource({ "hyperion-generation,1,true", "hyperion-generation,2,false", "document-check,1,false" })
    void healthOnlyCountsWorkersWithTheSupportedWorkloadSchema(String workload, int version, boolean compatible) {
        AiWorkerApi workers = mock();
        when(workers.statuses()).thenReturn(List.of(new WorkerStatusDTO("worker", WorkerState.AVAILABLE, Instant.now(), "sha256:" + "a".repeat(64), UUID.randomUUID(), null, false,
                1, 1, List.of(), new WorkloadCapabilityDTO(workload, version, "java-gradle"))));
        var registry = new GenerationWorkerRegistryService(workers, new WorkerMessageCodec());
        assertThat(registry.hasAvailableGenerationSandboxSlot()).isEqualTo(compatible);
        assertThat(registry.availableWorkers()).isEqualTo(compatible ? 1 : 0);
        assertThat(registry.capableWorkers()).isEqualTo(compatible ? 1 : 0);
        assertThat(registry.reachableWorkers()).isEqualTo(compatible ? 1 : 0);
    }
}
