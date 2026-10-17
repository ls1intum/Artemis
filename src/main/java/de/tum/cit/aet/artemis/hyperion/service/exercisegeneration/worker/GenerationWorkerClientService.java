package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker;

import java.util.function.Consumer;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.aiworker.api.AiWorkerApi;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerCommand;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerEvent;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerMessageCodec;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerRegistryService.Claim;

/** Typed Hyperion adapter for the shared worker transport. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationWorkerClientService {

    private final AiWorkerApi workers;

    private final WorkerMessageCodec codec;

    public GenerationWorkerClientService(AiWorkerApi workers, WorkerMessageCodec codec) {
        this.workers = workers;
        this.codec = codec;
    }

    public void send(WorkerCommand command) {
        workers.send(codec.toWire(command));
    }

    public void receive(Claim claim, Consumer<WorkerEvent> apply) {
        workers.receive(GenerationWorkerRegistryService.toWire(claim), event -> apply.accept(codec.fromWire(event)));
    }
}
