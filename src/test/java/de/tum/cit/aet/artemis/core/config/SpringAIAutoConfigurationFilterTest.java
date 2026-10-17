package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** The filter must agree with {@link ArtemisConfigHelper#isHyperionEnabled}: a Hyperion flag inherited by a standalone build agent must not load Spring AI. */
class SpringAIAutoConfigurationFilterTest {

    private static final String[] CANDIDATES = { "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
            "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration" };

    @Test
    void hyperionFlagWithoutCoreProfileExcludesSpringAi() {
        assertThat(match(environment(true, false), null)).containsExactly(false, true);
    }

    @Test
    void hyperionFlagWithCoreProfileIncludesSpringAi() {
        assertThat(match(environment(true, false), "core")).containsExactly(true, true);
    }

    @Test
    void atlasFlagIncludesSpringAiRegardlessOfProfile() {
        assertThat(match(environment(false, true), null)).containsExactly(true, true);
    }

    @Test
    void nullCandidatesAreTolerated() {
        var filter = new SpringAIAutoConfigurationFilter();
        filter.setEnvironment(environment(false, false));
        assertThat(filter.match(new String[] { null, CANDIDATES[0] }, null)).containsExactly(false, false);
    }

    private static boolean[] match(MockEnvironment environment, String profile) {
        if (profile != null) {
            environment.setActiveProfiles(profile);
        }
        var filter = new SpringAIAutoConfigurationFilter();
        filter.setEnvironment(environment);
        return filter.match(CANDIDATES, null);
    }

    private static MockEnvironment environment(boolean hyperion, boolean atlas) {
        return new MockEnvironment().withProperty(Constants.HYPERION_ENABLED_PROPERTY_NAME, String.valueOf(hyperion)).withProperty(Constants.ATLAS_ENABLED_PROPERTY_NAME,
                String.valueOf(atlas));
    }
}
