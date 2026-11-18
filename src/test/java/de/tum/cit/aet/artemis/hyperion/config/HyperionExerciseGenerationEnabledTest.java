package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.HYPERION_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.mock.env.MockEnvironment;

class HyperionExerciseGenerationEnabledTest {

    private static final String PROFILE_JENKINS = "jenkins";

    private static final String EXERCISE_GENERATION_ENABLED_PROPERTY_NAME = "artemis.hyperion.exercise-generation.enabled";

    private final HyperionExerciseGenerationEnabled condition = new HyperionExerciseGenerationEnabled();

    private static ConditionContext contextWith(MockEnvironment environment) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return context;
    }

    @ParameterizedTest(name = "hyperion={0}, exerciseGeneration={1}, core={2}, localci={3}, localvc={4} => {5}")
    @CsvSource({ "true,  true,  true,  true,  true,  true", // All independent gates are open.
            "false, true,  true,  true,  true,  false", // General Hyperion remains the parent gate.
            "true,  false, true,  true,  true,  false", // Whole-exercise generation can be dark-launched independently.
            "false, false, true,  true,  true,  false", // Neither property alone enables generation.
            "true,  true,  false, true,  true,  false", // Build-agent-only LocalCI node.
            "true,  true,  true,  false, true,  false", // Core node without LocalCI, including Jenkins.
            "true,  true,  true,  true,  false, false", // LocalCI core node without LocalVC.
            "true,  false, false, false, false, false", // Feature property cannot bypass any required profile.
            "false, true,  false, false, false, false" // Feature property cannot bypass Hyperion or required profiles.
    })
    void matchesOnlyWhenEveryGateIsEnabled(boolean hyperionEnabled, boolean exerciseGenerationEnabled, boolean coreProfileActive, boolean localCiProfileActive,
            boolean localVcProfileActive, boolean expected) {
        MockEnvironment environment = new MockEnvironment().withProperty(HYPERION_ENABLED_PROPERTY_NAME, Boolean.toString(hyperionEnabled))
                .withProperty(EXERCISE_GENERATION_ENABLED_PROPERTY_NAME, Boolean.toString(exerciseGenerationEnabled));
        List<String> profiles = new ArrayList<>();
        if (coreProfileActive) {
            profiles.add(PROFILE_CORE);
        }
        if (localCiProfileActive) {
            profiles.add(PROFILE_LOCALCI);
        }
        if (localVcProfileActive) {
            profiles.add(PROFILE_LOCALVC);
        }
        environment.setActiveProfiles(profiles.toArray(String[]::new));

        assertThat(condition.matches(contextWith(environment), null)).isEqualTo(expected);
    }

    @Test
    void doesNotMatchWhenExerciseGenerationPropertyIsMissing() {
        MockEnvironment environment = new MockEnvironment().withProperty(HYPERION_ENABLED_PROPERTY_NAME, "true");
        environment.setActiveProfiles(PROFILE_CORE, PROFILE_LOCALCI, PROFILE_LOCALVC);

        assertThat(condition.matches(contextWith(environment), null)).isFalse();
    }

    @Test
    void doesNotMatchUnderJenkinsWhenBothPropertiesAreEnabled() {
        MockEnvironment environment = new MockEnvironment().withProperty(HYPERION_ENABLED_PROPERTY_NAME, "true").withProperty(EXERCISE_GENERATION_ENABLED_PROPERTY_NAME, "true");
        environment.setActiveProfiles(PROFILE_JENKINS);

        assertThat(condition.matches(contextWith(environment), null)).isFalse();
    }
}
