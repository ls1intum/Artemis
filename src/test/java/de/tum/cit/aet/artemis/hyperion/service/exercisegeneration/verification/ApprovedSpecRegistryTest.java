package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApprovedSpecRegistryTest {

    @Test
    void approvalIsImmutableButIdempotent() {
        ApprovedSpecRegistry registry = new ApprovedSpecRegistry();

        registry.approve("session", "approved contract");
        registry.approve("session", "approved contract");

        assertThat(registry.approved("session")).contains("approved contract");
        assertThatThrownBy(() -> registry.approve("session", "weaker contract")).isInstanceOf(IllegalStateException.class).hasMessageContaining("different specification");
        assertThat(registry.approved("session")).contains("approved contract");
    }
}
