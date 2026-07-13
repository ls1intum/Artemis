package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.HYPERION_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.mock.env.MockEnvironment;

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
        environment.setActiveProfiles(PROFILE_CORE, PROFILE_LOCALCI);

        assertThat(condition.matches(contextWith(environment), null)).isTrue();
    }

    @Test
    void doesNotMatch_onBuildAgentOnlyLocalCiNode_whenCoreProfileAbsent() {
        MockEnvironment environment = new MockEnvironment().withProperty(HYPERION_ENABLED_PROPERTY_NAME, "true");
        environment.setActiveProfiles(PROFILE_LOCALCI);

        assertThat(condition.matches(contextWith(environment), null)).isFalse();
    }

    @Test
    void doesNotMatch_underJenkins_whenHyperionEnabledButLocalCiProfileAbsent() {
        MockEnvironment environment = new MockEnvironment().withProperty(HYPERION_ENABLED_PROPERTY_NAME, "true");
        environment.setActiveProfiles(PROFILE_JENKINS);

        assertThat(condition.matches(contextWith(environment), null)).isFalse();
    }

    @Test
    void doesNotMatch_whenHyperionDisabled_evenWithLocalCiProfileActive() {
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
