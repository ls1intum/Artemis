package de.tum.cit.aet.artemis.hyperion.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExecutionIdentityTest {

    @ParameterizedTest
    @ValueSource(strings = { "", "worker.commands", "../worker", "worker*", "worker/other", "worker\n" })
    void workerNameCannotEscapeItsBrokerDestination(String worker) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ExecutionIdentity("job", 1, UUID.randomUUID(), worker, UUID.randomUUID()));
    }

    @Test
    void restartOrReassignmentChangesIdentity() {
        var identity = new ExecutionIdentity("job", 1, UUID.randomUUID(), "worker-1", UUID.randomUUID());
        assertThat(identity).isNotEqualTo(new ExecutionIdentity(identity.jobId(), identity.exerciseId(), identity.executionId(), identity.workerId(), UUID.randomUUID()));
        assertThat(identity).isNotEqualTo(new ExecutionIdentity(identity.jobId(), identity.exerciseId(), UUID.randomUUID(), identity.workerId(), identity.workerIncarnation()));
    }
}
