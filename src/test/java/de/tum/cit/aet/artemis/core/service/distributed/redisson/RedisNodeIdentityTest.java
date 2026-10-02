package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RedisNodeIdentityTest {

    @Test
    void repeatedDisplayNameDoesNotReuseProcessIdentity() {
        var first = new RedisNodeIdentity("artemis-core");
        var replacement = new RedisNodeIdentity("artemis-core");
        assertThat(first.connectionName()).isNotEqualTo(replacement.connectionName());
        assertThat(first.connectionName()).isEqualTo(first.connectionName());
        assertThat(RedisNodeIdentity.isCoordinationName(first.connectionName())).isTrue();
        assertThat(RedisNodeIdentity.displayName(first.connectionName())).isEqualTo("artemis-core");
    }

    @Test
    void legacyAndMalformedNamesAreNotOwnershipEvidence() {
        for (String name : new String[] { "artemis-core", "", "artemis-coordination:", "artemis-coordination:00000000-0000-0000-0000-000000000000:",
                "artemis-coordination:zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz:artemis-core" }) {
            assertThat(RedisNodeIdentity.isCoordinationName(name)).isFalse();
            assertThat(RedisNodeIdentity.displayName(name)).isEqualTo(name);
        }
    }
}
