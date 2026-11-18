package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.aiworker.api.AiWorkerApi;
import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;
import de.tum.cit.aet.artemis.aiworker.service.WorkerClientService;
import de.tum.cit.aet.artemis.aiworker.service.WorkerRegistryService;
import de.tum.cit.aet.artemis.aiworker.service.messaging.WorkerTransport;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationToolchain;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerCommand;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerEvent;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerMessageCodec;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerRegistryService.Claim;

class GenerationWorkerClientServiceTest {

    private final WorkerMessageCodec codec = new WorkerMessageCodec();

    private final WorkerTransport transport = new WorkerTransport(new LocalDataProviderService(), new WorkerMessageCodecApi());

    private final ExecutionIdentity identity = new ExecutionIdentity("job", 1, UUID.randomUUID(), "worker", UUID.randomUUID());

    private final String digest = "sha256:" + "a".repeat(64);

    private final Claim claim = new Claim(identity, digest, GenerationToolchain.JAVA_GRADLE);

    private final GenerationWorkerClientService client = new GenerationWorkerClientService(new AiWorkerApi(mock(WorkerRegistryService.class), new WorkerClientService(transport)),
            codec);

    @Test
    void acknowledgesOnlyAfterCallbackCompletes() {
        publish(event(identity, digest));
        var calls = new AtomicInteger();
        var failure = new IllegalStateException("checkpoint store unavailable");
        assertThatThrownBy(() -> client.receive(claim, _ -> {
            calls.incrementAndGet();
            throw failure;
        })).isSameAs(failure);
        client.receive(claim, _ -> calls.incrementAndGet());
        assertThat(calls).hasValue(2);
        assertThat(transport.receive(GenerationWorkerRegistryService.toWire(claim).identity(), _ -> {
            throw new AssertionError("acknowledged event returned");
        })).isFalse();
    }

    @Test
    void rejectsOtherImageBeforeCallback() {
        publish(event(identity, "sha256:" + "b".repeat(64)));
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> client.receive(claim, _ -> calls.incrementAndGet())).isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).hasValue(0);
    }

    private void publish(WorkerEvent event) {
        transport.publish(codec.toWire(event));
    }

    private WorkerEvent event(ExecutionIdentity execution, String image) {
        return new WorkerEvent(WorkerCommand.PROTOCOL_VERSION, "worker", identity.workerIncarnation(), 1, Instant.now(), WorkerEvent.Type.STARTED, execution, false, image, null,
                null, null);
    }
}
