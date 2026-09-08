package de.tum.cit.aet.artemis.buildagent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.mock.env.MockEnvironment;

class GenerationSandboxHostingEnabledTest {

    private static final String MAX_GENERATION_SANDBOX_SLOTS_PROPERTY = "artemis.continuous-integration.build-agent.max-generation-sandbox-slots";

    private final GenerationSandboxHostingEnabled condition = new GenerationSandboxHostingEnabled();

    @ParameterizedTest
    @CsvSource({ "0, false", "1, true", "4, true" })
    void matchesOnlyForPositiveGenerationSandboxCapacity(int slots, boolean expected) {
        MockEnvironment environment = new MockEnvironment().withProperty(MAX_GENERATION_SANDBOX_SLOTS_PROPERTY, Integer.toString(slots));

        assertThat(condition.matches(contextWith(environment), null)).isEqualTo(expected);
    }

    @Test
    void rejectsNegativeGenerationSandboxCapacity() {
        MockEnvironment environment = new MockEnvironment().withProperty(MAX_GENERATION_SANDBOX_SLOTS_PROPERTY, "-1");

        assertThatIllegalArgumentException().isThrownBy(() -> condition.matches(contextWith(environment), null)).withMessageContaining(MAX_GENERATION_SANDBOX_SLOTS_PROPERTY);
    }

    @Test
    void doesNotMatchWhenGenerationSandboxCapacityIsMissing() {
        assertThat(condition.matches(contextWith(new MockEnvironment()), null)).isFalse();
    }

    private static ConditionContext contextWith(MockEnvironment environment) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return context;
    }
}
