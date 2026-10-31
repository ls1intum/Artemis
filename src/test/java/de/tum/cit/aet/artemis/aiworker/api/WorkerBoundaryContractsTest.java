package de.tum.cit.aet.artemis.aiworker.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.aiworker.config.WorkerSettings;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerEventType;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionAssignmentDTO;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionIdentityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.aiworker.dto.SandboxPolicyDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCapacityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCommandDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;

/** Security-relevant transport and deployment boundaries, including exact accepted limits. */
class WorkerBoundaryContractsTest {

    private static final UUID INCARNATION = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID EXECUTION = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final String IMAGE = "sha256:" + "a".repeat(64);

    private static final WorkloadCapabilityDTO CAPABILITY = new WorkloadCapabilityDTO("hyperion", 1, "default");

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEvents")
    void rejectsInvalidOrInconsistentEvents(String reason, Consumer<EventFixture> mutation) {
        var fixture = new EventFixture();
        mutation.accept(fixture);
        assertThatThrownBy(fixture::build).as(reason).isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Arguments> invalidEvents() {
        return Stream.of(invalid("unsupported protocol", f -> f.protocol = -1), invalid("missing worker", f -> f.worker = null),
                invalid("unsafe worker", f -> f.worker = "../worker"), invalid("missing incarnation", f -> f.incarnation = null),
                invalid("nonpositive sequence", f -> f.sequence = 0), invalid("missing timestamp", f -> f.timestamp = null), invalid("missing type", f -> f.type = null),
                invalid("missing image", f -> f.image = null), invalid("mutable image tag", f -> f.image = "worker:latest"), invalid("missing capacity", f -> f.capacity = null),
                invalid("missing capability", f -> f.capability = null), invalid("oversized message", f -> f.message = "m".repeat(8193)), invalid("accounting limit", f -> {
                    f.type = WorkerEventType.ACCOUNTING;
                    f.payload = "p".repeat(8193);
                }), invalid("foreign identity worker", f -> f.identity = identity("other", INCARNATION, 0)),
                invalid("foreign identity incarnation", f -> f.identity = identity("worker", EXECUTION, 0)),
                invalid("foreign capacity worker", f -> f.capacity = new WorkerCapacityDTO(1, List.of(identity("other", INCARNATION, 0)))),
                invalid("foreign capacity incarnation", f -> f.capacity = new WorkerCapacityDTO(1, List.of(identity("worker", EXECUTION, 0)))),
                invalid("execution event requires identity", f -> f.identity = null), invalid("checkpoint requires payload", f -> {
                    f.type = WorkerEventType.CHECKPOINT;
                    f.payload = null;
                }), invalid("finished requires payload", f -> {
                    f.type = WorkerEventType.FINISHED;
                    f.payload = null;
                }));
    }

    private static Arguments invalid(String reason, Consumer<EventFixture> mutation) {
        return Arguments.of(reason, mutation);
    }

    @Test
    void acceptsExactAccountingAndMessageLimitsAndCopiesOnlyRequestedEventFields() {
        var fixture = new EventFixture();
        fixture.type = WorkerEventType.ACCOUNTING;
        fixture.payload = "p".repeat(8192);
        fixture.message = "m".repeat(8192);
        var event = fixture.build();
        var capacity = new WorkerCapacityDTO(2, List.of(fixture.identity));
        var changed = event.withCapacity(capacity);

        assertThat(changed.capacity()).isEqualTo(capacity);
        assertThat(changed.identity()).isEqualTo(event.identity());
        assertThat(changed.payload()).isEqualTo(event.payload());
        assertThat(changed.message()).hasSize(8192);
        assertThat(event.capacity().slots()).isOne();
        var capability = new WorkloadCapabilityDTO("hyperion", 2, "restricted");
        assertThat(event.withCapability(capability).capability()).isEqualTo(capability);
        assertThat(event.capability()).isEqualTo(CAPABILITY);
        fixture.type = WorkerEventType.HEARTBEAT;
        fixture.identity = null;
        fixture.payload = null;
        assertThat(fixture.build().identity()).isNull();
    }

    @ParameterizedTest
    @CsvSource(value = { "NULL, resource, worker, 0", "'', resource, worker, 0", "' ', resource, worker, 0", "job, NULL, worker, 0", "job, '', worker, 0", "job, ' ', worker, 0",
            "job, resource, NULL, 0", "job, resource, bad/worker, 0", "job, resource, worker, -1", "job, resource, worker, 16" }, nullValues = "NULL")
    void rejectsUnsafeExecutionCorrelations(String job, String resource, String worker, int slot) {
        assertThatThrownBy(() -> new ExecutionIdentityDTO(job, resource, EXECUTION, worker, INCARNATION, slot)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid execution identity");
    }

    @Test
    void enforcesExecutionIdentityLengthAndRequiredAuthorityWithExactBoundaries() {
        assertThatThrownBy(() -> new ExecutionIdentityDTO("x".repeat(129), "resource", EXECUTION, "worker", INCARNATION, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionIdentityDTO("job", "x".repeat(129), EXECUTION, "worker", INCARNATION, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionIdentityDTO("job", "resource", null, "worker", INCARNATION, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionIdentityDTO("job", "resource", EXECUTION, "worker", null, 0)).isInstanceOf(IllegalArgumentException.class);
        var identity = new ExecutionIdentityDTO("j".repeat(128), "r".repeat(128), EXECUTION, "w".repeat(64), INCARNATION, 15);
        assertThat(identity.slot()).isEqualTo(15);
        assertThat(identity.jobId()).hasSize(128);
        assertThat(identity.resourceId()).hasSize(128);
        assertThat(identity.workerId()).hasSize(64);
    }

    @ParameterizedTest
    @CsvSource(value = { "NULL, NULL, ''", "'', NULL, ''", "NULL, '', ''", "out, NULL, out", "NULL, err, err", "'', err, err", "out, '', out",
            "out, err, 'out\nerr'" }, nullValues = "NULL")
    void combinesCommandOutputWithoutNullTextOrExtraSeparators(String stdout, String stderr, String expected) {
        var result = new SandboxExecResultDTO(0, stdout, stderr, false);
        assertThat(result.combinedOutput()).isEqualTo(expected);
        assertThat(result.isSuccess()).isTrue();
        assertThat(new SandboxExecResultDTO(1, stdout, stderr, false).isSuccess()).isFalse();
        assertThat(new SandboxExecResultDTO(0, stdout, stderr, true).isSuccess()).isFalse();
    }

    @Test
    void rejectsMissingAssignmentAuthorityAndBlankWorkloadData() {
        var id = identity("worker", INCARNATION, 0);
        var deadline = Instant.parse("2030-01-01T00:00:00Z");
        assertThatThrownBy(() -> new ExecutionAssignmentDTO(null, CAPABILITY, deadline, IMAGE, "payload")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionAssignmentDTO(id, null, deadline, IMAGE, "payload")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionAssignmentDTO(id, CAPABILITY, null, IMAGE, "payload")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionAssignmentDTO(id, CAPABILITY, deadline, null, "payload")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionAssignmentDTO(id, CAPABILITY, deadline, "repo:latest", "payload")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionAssignmentDTO(id, CAPABILITY, deadline, IMAGE, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionAssignmentDTO(id, CAPABILITY, deadline, IMAGE, " ")).isInstanceOf(IllegalArgumentException.class);
        var assignment = new ExecutionAssignmentDTO(id, CAPABILITY, deadline, IMAGE, "payload");
        assertThat(assignment.identity()).isEqualTo(id);
        assertThat(assignment.deadline()).isEqualTo(deadline);
        assertThat(assignment.payload()).isEqualTo("payload");
    }

    @ParameterizedTest
    @ValueSource(strings = { "/", "relative/path", "/../escape", "/a\\b", "/a\0b", "//./" })
    void rejectsSandboxMountsThatEscapeTheContainerPolicy(String mount) {
        assertThatThrownBy(() -> policy(Map.of(mount, "size=1m"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mounts");
    }

    @Test
    void normalizesSandboxMountsDetectsCollisionsAndDefensivelyCopiesThePolicy() {
        var mounts = new HashMap<String, String>();
        mounts.put("//workspace/./tmp/", "size=1m");
        var policy = policy(mounts);
        mounts.clear();
        assertThat(policy.writableFilesystems()).containsExactlyEntriesOf(Map.of("/workspace/tmp", "size=1m"));
        assertThatThrownBy(() -> policy.writableFilesystems().put("/new", "size=2m")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> policy(Map.of("/tmp", "size=1m", "//tmp/", "size=2m"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("distinct");
    }

    @ParameterizedTest
    @CsvSource(value = { "NULL, 1, 1, 1, 10, 45, 120", "'', 1, 1, 1, 10, 45, 120", "' ', 1, 1, 1, 10, 45, 120", "runc, 0, 1, 1, 10, 45, 120", "runc, 1, 0, 1, 10, 45, 120",
            "runc, 1, 1, 0, 10, 45, 120", "runc, 1, 1, 1, 0, 45, 120", "runc, 1, 1, 1, 10, 10, 120", "runc, 1, 1, 1, 10, 9, 120", "runc, 1, 1, 1, 10, 45, 0" }, nullValues = "NULL")
    void rejectsWorkerLimitsThatCannotProvideAnIsolatedLiveExecution(String runtime, long memory, long cpu, long pids, int heartbeat, int grace, int shutdown) {
        assertThatThrownBy(() -> new WorkerSettings("worker", IMAGE, runtime, memory, cpu, pids, Duration.ofSeconds(heartbeat), Duration.ofSeconds(grace),
                Duration.ofSeconds(shutdown), 1, "hyperion", "default")).isInstanceOf(IllegalArgumentException.class);
    }

    private static SandboxPolicyDTO policy(Map<String, String> mounts) {
        return new SandboxPolicyDTO("worker", IMAGE, "runc", 1024, 1000, 16, "artemis.worker", "worker-", mounts);
    }

    @Test
    void rejectsMissingWorkerTimingAndUnsafeDeploymentIdentityBeforeStartingAnyWork() {
        var heartbeat = Duration.ofSeconds(1);
        var grace = Duration.ofSeconds(2);
        var shutdown = Duration.ofSeconds(1);
        assertThatThrownBy(() -> settings(null, IMAGE, 1, heartbeat, grace, shutdown)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("worker id");
        assertThatThrownBy(() -> settings("bad/worker", IMAGE, 1, heartbeat, grace, shutdown)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("worker id");
        assertThatThrownBy(() -> settings("worker", "registry.example.org/worker:bad tag", 1, heartbeat, grace, shutdown)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("image");
        assertThatThrownBy(() -> settings("worker", "worker:latest", 1, heartbeat, grace, shutdown)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("image");
        assertThatThrownBy(() -> settings("worker", IMAGE, 0, heartbeat, grace, shutdown)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("execution slots");
        assertThatThrownBy(() -> settings("worker", IMAGE, 17, heartbeat, grace, shutdown)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("execution slots");
        assertThatThrownBy(() -> settings("worker", IMAGE, 1, null, grace, shutdown)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("heartbeat");
        assertThatThrownBy(() -> settings("worker", IMAGE, 1, heartbeat, null, shutdown)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("heartbeat");
        assertThatThrownBy(() -> settings("worker", IMAGE, 1, heartbeat, grace, null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("heartbeat");
        var accepted = settings("w".repeat(64), "registry.example.org/worker@" + IMAGE, 16, heartbeat, grace, shutdown);
        assertThat(accepted.maxConcurrentExecutions()).isEqualTo(16);
        assertThat(accepted.capability()).isEqualTo(CAPABILITY);
        assertThat(accepted.connectionGrace()).isGreaterThan(accepted.heartbeatInterval());
    }

    private static WorkerSettings settings(String id, String image, int slots, Duration heartbeat, Duration grace, Duration shutdown) {
        return new WorkerSettings(id, image, "runc", 1024, 1000, 16, heartbeat, grace, shutdown, slots, "hyperion", "default");
    }

    private static ExecutionIdentityDTO identity(String worker, UUID incarnation, int slot) {
        return new ExecutionIdentityDTO("job", "resource", EXECUTION, worker, incarnation, slot);
    }

    private static final class EventFixture {

        private int protocol = WorkerCommandDTO.PROTOCOL_VERSION;

        private String worker = "worker";

        private UUID incarnation = INCARNATION;

        private long sequence = 1;

        private Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");

        private WorkerEventType type = WorkerEventType.PROGRESS;

        private ExecutionIdentityDTO identity = identity("worker", INCARNATION, 0);

        private String image = IMAGE;

        private String message;

        private String payload = "payload";

        private WorkerCapacityDTO capacity = new WorkerCapacityDTO(1, List.of());

        private WorkloadCapabilityDTO capability = CAPABILITY;

        private WorkerEventDTO build() {
            return new WorkerEventDTO(protocol, worker, incarnation, sequence, timestamp, type, identity, true, image, message, payload, capacity, capability);
        }
    }
}
