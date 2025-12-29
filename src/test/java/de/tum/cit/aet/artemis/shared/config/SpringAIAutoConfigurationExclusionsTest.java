package de.tum.cit.aet.artemis.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;

/**
 * Verifies that Spring AI autoconfigurations are excluded when Hyperion and Atlas are disabled.
 * This test explicitly disables both features to override the 'local' profile defaults.
 * The SpringAIAutoConfigurationFilter should exclude all Spring AI autoconfiguration classes.
 * This ensures build agents and other contexts without AI features don't load unnecessary beans.
 */
class SpringAIAutoConfigurationExclusionsTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    @Autowired
    private ApplicationContext applicationContext;

    // Temporarily disabled because AbstractSpringIntegrationJenkinsLocalVCTest needs atlas enabled and this test
    // would only work if atlas is disabled. However, this would need another server start that we want to avoid
    // A larger refactoring of the server tests would be required to fix this properly.
    @Disabled
    void testSpringAIAutoConfigurationsExcluded() {
        // When both Hyperion and Atlas are disabled, Spring AI autoconfigurations are filtered out
        // by SpringAIAutoConfigurationFilter. This means no ChatModel beans should be created.
        // We verify this by checking that no beans of type ChatModel exist in the context.
        assertThat(applicationContext.getBeanNamesForType(ChatModel.class)).isEmpty();
    }
}
