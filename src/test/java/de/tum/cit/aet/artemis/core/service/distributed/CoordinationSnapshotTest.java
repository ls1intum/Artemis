package de.tum.cit.aet.artemis.core.service.distributed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.service.distributed.api.CoordinationSnapshot;

class CoordinationSnapshotTest {

    @Test
    void memberHostedStoreRequiresExactAdmissionAndStrictMajorityRecovery() {
        var snapshot = new CoordinationSnapshot(Set.of("one", "two"), true);
        assertThat(snapshot.permitsAdmission(2)).isTrue();
        assertThat(snapshot.permitsAdmission(3)).isFalse();
        assertThat(snapshot.permitsAdmission(1)).isFalse();
        assertThat(snapshot.permitsRecovery(3)).isTrue();
        assertThat(snapshot.permitsRecovery(4)).isFalse();
        assertThat(snapshot.permitsRecovery(1)).isFalse();
        assertThat(new CoordinationSnapshot(Set.of("one"), true).permitsRecovery(2)).isFalse();
    }

    @Test
    void centralStoreDoesNotCountClientsAsDataMembers() {
        var snapshot = new CoordinationSnapshot(Set.of("one", "two"), false);
        assertThat(snapshot.permitsAdmission(1)).isTrue();
        assertThat(snapshot.permitsAdmission(3)).isTrue();
        assertThat(snapshot.permitsRecovery(5)).isTrue();
    }

    @Test
    void emptyViewAndInvalidConfigurationFailClosed() {
        for (boolean memberHosted : new boolean[] { true, false }) {
            assertThat(new CoordinationSnapshot(Set.of(), memberHosted).permitsAdmission(1)).isFalse();
            assertThat(new CoordinationSnapshot(Set.of(), memberHosted).permitsRecovery(1)).isFalse();
            var snapshot = new CoordinationSnapshot(Set.of("one"), memberHosted);
            assertThat(snapshot.permitsAdmission(0)).isFalse();
            assertThat(snapshot.permitsRecovery(-1)).isFalse();
        }
    }

    @Test
    void snapshotDoesNotRetainMutableMembership() {
        var members = new HashSet<>(Set.of("one"));
        var snapshot = new CoordinationSnapshot(members, true);
        members.clear();
        assertThat(snapshot.ownerNodeIds()).containsExactly("one");
    }
}
