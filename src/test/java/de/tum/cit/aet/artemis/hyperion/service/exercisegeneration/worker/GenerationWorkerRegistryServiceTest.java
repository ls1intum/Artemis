package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

import de.tum.cit.aet.artemis.aiworker.api.AiWorkerApi;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerState;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerStatusDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerMessageCodec;

class GenerationWorkerRegistryServiceTest {

    @Test
    void oneIndexedWorkerIdIsAccepted() {
        var environment = new MockEnvironment().withProperty("artemis.aiworker.ids[0]", "worker");

        assertThatCode(() -> new GenerationWorkerRegistryService(mock(AiWorkerApi.class), new WorkerMessageCodec(), environment)).doesNotThrowAnyException();
    }

    @Test
    void missingWorkerIdIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GenerationWorkerRegistryService(mock(AiWorkerApi.class), new WorkerMessageCodec(), new MockEnvironment()))
                .withMessage("Hyperion exercise generation requires exactly one configured AI worker ID");
    }

    @Test
    void twoCommaSeparatedWorkerIdsAreRejected() {
        var environment = new MockEnvironment().withProperty("artemis.aiworker.ids", "worker-one,worker-two");

        assertThatIllegalArgumentException().isThrownBy(() -> new GenerationWorkerRegistryService(mock(AiWorkerApi.class), new WorkerMessageCodec(), environment))
                .withMessage("Hyperion exercise generation requires exactly one configured AI worker ID");
    }

    @Test
    void twoIndexedWorkerIdsAreRejected() {
        var environment = new MockEnvironment().withProperty("artemis.aiworker.ids[0]", "worker-one").withProperty("artemis.aiworker.ids[1]", "worker-two");

        assertThatIllegalArgumentException().isThrownBy(() -> new GenerationWorkerRegistryService(mock(AiWorkerApi.class), new WorkerMessageCodec(), environment))
                .withMessage("Hyperion exercise generation requires exactly one configured AI worker ID");
    }

    @ParameterizedTest
    @CsvSource({ "hyperion-generation,1,true", "hyperion-generation,2,false", "document-check,1,false" })
    void healthOnlyCountsWorkersWithTheSupportedWorkloadSchema(String workload, int version, boolean compatible) {
        AiWorkerApi workers = mock();
        when(workers.statuses()).thenReturn(List.of(new WorkerStatusDTO("worker", WorkerState.AVAILABLE, Instant.now(), "sha256:" + "a".repeat(64), UUID.randomUUID(), null, false,
                1, 1, List.of(), new WorkloadCapabilityDTO(workload, version, "java-gradle"))));
        var registry = new GenerationWorkerRegistryService(workers, new WorkerMessageCodec(), new MockEnvironment().withProperty("artemis.aiworker.ids", "worker"));
        assertThat(registry.hasAvailableGenerationSandboxSlot()).isEqualTo(compatible);
        assertThat(registry.availableWorkers()).isEqualTo(compatible ? 1 : 0);
        assertThat(registry.capableWorkers()).isEqualTo(compatible ? 1 : 0);
        assertThat(registry.reachableWorkers()).isEqualTo(compatible ? 1 : 0);
    }
}
