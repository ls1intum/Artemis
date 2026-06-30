package de.tum.cit.aet.artemis.iris.struggle;

import static de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome.ABANDONED;
import static de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome.RECOVERED;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;

/**
 * Unit test verifying that all three IrisProactiveOutcome values are present and resolvable by name.
 * Enum tests are fast (no Spring context needed).
 */
class IrisProactiveOutcomeTest {

    @Test
    void enumContainsRecoveredAndAbandoned() {
        assertThat(IrisProactiveOutcome.values()).contains(RECOVERED, ABANDONED);
    }

    @Test
    void valueOfRecoveredResolves() {
        assertThat(IrisProactiveOutcome.valueOf("RECOVERED")).isEqualTo(RECOVERED);
    }

    @Test
    void valueOfAbandonedResolves() {
        assertThat(IrisProactiveOutcome.valueOf("ABANDONED")).isEqualTo(ABANDONED);
    }
}
