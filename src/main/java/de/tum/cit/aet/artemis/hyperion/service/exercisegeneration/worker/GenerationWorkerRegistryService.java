package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.aiworker.api.AiWorkerApi;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionClaimDTO;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationToolchain;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerEvent;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerMessageCodec;

/** Hyperion admission policy; shared worker ownership is exclusively managed by AI Worker. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationWorkerRegistryService {

    private final AiWorkerApi workers;

    private final WorkerMessageCodec codec;

    public GenerationWorkerRegistryService(AiWorkerApi workers, WorkerMessageCodec codec) {
        this.workers = workers;
        this.codec = codec;
    }

    public boolean hasAvailableGenerationSandboxSlot() {
        return workers.statuses().stream()
                .anyMatch(s -> s.capability() != null && s.capability().workload().equals("hyperion-generation") && s.capability().version() == 1 && s.availableSlots() > 0);
    }

    public boolean hasAvailableGenerationSandboxSlot(GenerationToolchain toolchain) {
        return workers.available(WorkerMessageCodec.capability(toolchain));
    }

    public Claim claim(String jobId, long exerciseId, GenerationToolchain toolchain) {
        var claim = workers.claim(jobId, Long.toString(exerciseId), WorkerMessageCodec.capability(toolchain));
        return new Claim(WorkerMessageCodec.fromWire(claim.identity()), claim.imageDigest(), toolchain);
    }

    public boolean renew(Claim claim) {
        return workers.renew(toWire(claim));
    }

    public void release(Claim claim) {
        workers.release(toWire(claim));
    }

    public void recordCompletion(Claim claim, WorkerEvent event) {
        workers.recordCompletion(toWire(claim), codec.toWire(event));
    }

    public int reachableWorkers() {
        return (int) workers.statuses().stream().filter(s -> s.capability() != null && s.capability().workload().equals("hyperion-generation") && s.capability().version() == 1)
                .count();
    }

    public int capableWorkers() {
        return (int) workers.statuses().stream().filter(s -> s.capability() != null && s.capability().workload().equals("hyperion-generation") && s.capability().version() == 1
                && (s.availableSlots() > 0 || !s.executions().isEmpty())).count();
    }

    public int availableWorkers() {
        return (int) workers.statuses().stream()
                .filter(s -> s.capability() != null && s.capability().workload().equals("hyperion-generation") && s.capability().version() == 1 && s.availableSlots() > 0).count();
    }

    public static ExecutionClaimDTO toWire(Claim claim) {
        return new ExecutionClaimDTO(WorkerMessageCodec.toWire(claim.identity()), claim.imageDigest(), WorkerMessageCodec.capability(claim.toolchain()));
    }

    public record Claim(ExecutionIdentity identity, String imageDigest, GenerationToolchain toolchain) {
    }
}
