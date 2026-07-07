package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.HYPERION_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit test for {@link HyperionExerciseGenerationEnabled}: the agentic exercise-generation feature is registered only when Hyperion is enabled AND the integrated {@code localci}
 * profile is active. On a Jenkins deployment (Hyperion on, but no {@code localci} profile) the whole generation stack — REST controller, orchestration engine, job/task services,
 * revert service — must stay inert, because generation is built entirely on the LocalCI/LocalVC lifecycle. This condition is the single gate that enforces that, so it is tested
 * directly against a real {@link MockEnvironment} driving the production {@code ArtemisConfigHelper} property read and {@code Environment#acceptsProfiles} check.
 */
class HyperionExerciseGenerationEnabledTest {

    private static final String PROFILE_JENKINS = "jenkins";

    private final HyperionExerciseGenerationEnabled condition = new HyperionExerciseGenerationEnabled();

    private static ConditionContext contextWith(MockEnvironment environment) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return context;
    }

    @Test
    void matches_whenHyperionEnabledAndLocalCiProfileActive() {
        MockEnvironment environment = new MockEnvironment().withProperty(HYPERION_ENABLED_PROPERTY_NAME, "true");
        environment.setActiveProfiles(PROFILE_LOCALCI);

        assertThat(condition.matches(contextWith(environment), null)).isTrue();
    }

    @Test
    void doesNotMatch_underJenkins_whenHyperionEnabledButLocalCiProfileAbsent() {
        // The exact Jenkins deployment shape: Hyperion is on, but the integrated LocalCI profile is not active — generation must not register.
        MockEnvironment environment = new MockEnvironment().withProperty(HYPERION_ENABLED_PROPERTY_NAME, "true");
        environment.setActiveProfiles(PROFILE_JENKINS);

        assertThat(condition.matches(contextWith(environment), null)).isFalse();
    }

    @Test
    void doesNotMatch_whenHyperionDisabled_evenWithLocalCiProfileActive() {
        // Disabling Hyperion must switch generation off regardless of the CI profile: the localci arm alone is not sufficient.
        MockEnvironment environment = new MockEnvironment().withProperty(HYPERION_ENABLED_PROPERTY_NAME, "false");
        environment.setActiveProfiles(PROFILE_LOCALCI);

        assertThat(condition.matches(contextWith(environment), null)).isFalse();
    }

    @Test
    void doesNotMatch_whenHyperionDisabledAndNoLocalCiProfile() {
        MockEnvironment environment = new MockEnvironment().withProperty(HYPERION_ENABLED_PROPERTY_NAME, "false");

        assertThat(condition.matches(contextWith(environment), null)).isFalse();
    }
}
