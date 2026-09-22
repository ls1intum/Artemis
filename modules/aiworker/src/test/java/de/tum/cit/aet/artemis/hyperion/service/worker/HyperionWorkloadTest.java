package de.tum.cit.aet.artemis.hyperion.service.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.aiworker.dto.ExecutionAssignmentDTO;
import de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity;
import de.tum.cit.aet.artemis.hyperion.protocol.ExerciseBrief;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationAssignment;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationParameters;
import de.tum.cit.aet.artemis.hyperion.protocol.GradingContext;
import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.protocol.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerCommand;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerMessageCodec;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceSnapshot;

class HyperionWorkloadTest {

    private final GenerationEngine engine = mock();

    private final HyperionWorkloadService workload = new HyperionWorkloadService(engine, "java-gradle");

    private final WorkerMessageCodec codec = new WorkerMessageCodec();

    @Test
    void validatesAuthorityBeforeCallingTheDomainEngine() {
        var assignment = assignment();
        var wrong = new ExecutionAssignmentDTO(assignment.identity(), assignment.capability(), assignment.deadline().plusSeconds(1), assignment.imageDigest(),
                assignment.payload());
        assertThatThrownBy(() -> workload.execute(wrong, () -> false, mock(), mock())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(engine);
    }

    @Test
    void unknownPayloadFieldsDoNotReachGeneration() {
        var assignment = assignment();
        var wrong = new ExecutionAssignmentDTO(assignment.identity(), assignment.capability(), assignment.deadline(), assignment.imageDigest(),
                assignment.payload().replaceFirst("\\{", "{\"shell\":\"untrusted\","));
        assertThatThrownBy(() -> workload.execute(wrong, () -> false, mock(), mock())).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(engine);
    }

    @Test
    void roundTripsTypedResultAndExactCancellationIdentity() {
        var output = new GenerationOutput(new WorkspaceSnapshot(List.of()), new VerificationResult(false, false, false, 0, List.of()), null, new SpecFidelityReport(List.of()),
                "RUN_FAILED", null, GenerationOutput.AccountingState.INCOMPLETE, "standard");
        when(engine.generate(any(), any(), any(), any())).thenReturn(output);
        var assignment = assignment();
        assertThat(codec.decodePayload(workload.execute(assignment, () -> false, mock(), mock()), GenerationOutput.class)).isEqualTo(output);
        workload.requestCancel(assignment.identity());
        verify(engine).requestCancel(WorkerMessageCodec.fromWire(assignment.identity()));
    }

    private ExecutionAssignmentDTO assignment() {
        var identity = new ExecutionIdentity("job", 42, UUID.randomUUID(), "worker", UUID.randomUUID());
        var input = new GenerationAssignment(identity, new ExerciseBrief("Stack", "stack", "de.example", null, "Create", ExerciseBrief.Mode.GENERATE),
                new GenerationParameters("standard", 10, 100000, Duration.ofMinutes(5), 128000, null, null, null, null, null, true, "CONTINUOUS"), new WorkspaceSnapshot(List.of()),
                Instant.now().plusSeconds(300), "sha256:" + "a".repeat(64), new GradingContext(false, Set.of()));
        return codec.toWire(new WorkerCommand(WorkerCommand.PROTOCOL_VERSION, WorkerCommand.Type.START, identity, input)).assignment();
    }
}
