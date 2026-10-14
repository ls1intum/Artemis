package de.tum.cit.aet.artemis.core.service.distributed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.service.distributed.api.CoordinationSnapshot;

class CoordinationSnapshotTest {

    @Test
    void observedProcessesAreCapturedWithoutMutableMembershipOrWriterSafetyClaims() {
        var processes = new HashSet<>(Set.of("one"));
        var snapshot = new CoordinationSnapshot(processes);
        processes.add("two");
        assertThat(snapshot.ownerNodeIds()).containsExactly("one");
    }
}
