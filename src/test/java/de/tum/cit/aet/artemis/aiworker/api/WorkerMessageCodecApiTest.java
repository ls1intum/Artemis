package de.tum.cit.aet.artemis.aiworker.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.aiworker.domain.WorkerCommandType;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionAssignmentDTO;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionIdentityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCommandDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;

class WorkerMessageCodecApiTest {

    private final WorkerMessageCodecApi codec = new WorkerMessageCodecApi();

    @Test
    void arbitraryWorkloadPayloadRemainsOpaqueAndRoundTripsOnce() {
        var command = start("{\"nested\":\"\\\"quoted\\\"\"}");
        assertThat(codec.decodeCommand(codec.encode(command))).isEqualTo(command);
    }

    @Test
    void rejectsDuplicateUnknownAndIncompatibleEnvelopeFields() {
        String body = codec.encode(start("document"));
        assertThatThrownBy(() -> codec.decodeCommand(body.replaceFirst("\\{", "{\"protocolVersion\":4,"))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> codec.decodeCommand(body.replaceFirst("\\{", "{\"unknown\":true,"))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> codec.decodeCommand(body.replace("\"protocolVersion\":4", "\"protocolVersion\":3"))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsNestedAssignmentForAnotherExecution() {
        var command = start("document");
        var another = start("another");
        assertThatThrownBy(() -> new WorkerCommandDTO(4, WorkerCommandType.START, command.identity(), another.assignment())).isInstanceOf(IllegalArgumentException.class);
    }

    private WorkerCommandDTO start(String payload) {
        var identity = new ExecutionIdentityDTO("document-job", "document:abc", UUID.randomUUID(), "worker", UUID.randomUUID(), 0);
        return new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.START, identity, new ExecutionAssignmentDTO(identity,
                new WorkloadCapabilityDTO("document-check", 1, "plain-text"), Instant.now().plusSeconds(30), "sha256:" + "a".repeat(64), payload));
    }
}
