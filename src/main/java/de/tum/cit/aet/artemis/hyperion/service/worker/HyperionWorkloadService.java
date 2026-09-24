package de.tum.cit.aet.artemis.hyperion.service.worker;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.aiworker.api.ExecutionObserver;
import de.tum.cit.aet.artemis.aiworker.api.WorkloadApi;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionAssignmentDTO;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionIdentityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationActivity;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationEventPayloadDTO;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationProgress;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationToolchain;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerMessageCodec;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.GenerationActivityTracker;

/** The only translation between generic worker execution and typed Hyperion authoring. */
@Service
@Lazy
@Profile(PROFILE_AIWORKER)
@ConditionalOnProperty(name = "artemis.aiworker.workload", havingValue = "hyperion-generation")
public class HyperionWorkloadService implements WorkloadApi {

    private final GenerationEngine engine;

    private final WorkloadCapabilityDTO capability;

    private final WorkerMessageCodec codec = new WorkerMessageCodec();

    public HyperionWorkloadService(GenerationEngine engine, @Value("${artemis.aiworker.profile}") String profile) {
        this.engine = engine;
        capability = WorkerMessageCodec.capability(new GenerationToolchain(profile));
    }

    @Override
    public WorkloadCapabilityDTO capability() {
        return capability;
    }

    @Override
    public String execute(ExecutionAssignmentDTO assignment, BooleanSupplier stopping, ExecutionObserver observer, Consumer<String> checkpoint) {
        if (!capability.equals(assignment.capability())) {
            throw new IllegalArgumentException("Unsupported Hyperion workload capability");
        }
        var input = codec.decodeAssignment(assignment);
        GenerationObserver progress = new GenerationObserver() {

            private final GenerationActivityTracker tracker = new GenerationActivityTracker();

            @Override
            public GenerationActivityTracker activityTracker() {
                return tracker;
            }

            @Override
            public void accept(String message) {
                observer.progress(message, null, false);
            }

            @Override
            public void activity(String message, GenerationActivity activity) {
                observer.progress(message, codec.encodePayload(new GenerationEventPayloadDTO(activity, null)), false);
            }

            @Override
            public void progress(String message, GenerationProgress detail) {
                observer.progress(message, codec.encodePayload(new GenerationEventPayloadDTO(null, detail)), detail.usage() != null);
            }
        };
        return codec.encodePayload(engine.generate(input, stopping, progress, candidate -> checkpoint.accept(codec.encodePayload(candidate))));
    }

    @Override
    public boolean requestCancel(ExecutionIdentityDTO identity) {
        return engine.requestCancel(WorkerMessageCodec.fromWire(identity));
    }
}
