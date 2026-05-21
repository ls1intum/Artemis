package de.tum.cit.aet.artemis.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * Verifies that Spring AI autoconfigurations are included when Hyperion is enabled.
 * AbstractSpringIntegrationLocalCILocalVCTest has artemis.hyperion.enabled=true,
 * so the SpringAIAutoConfigurationFilter should allow Spring AI autoconfiguration classes.
 * This ensures AI features are available when the Hyperion module is active.
 */
// AbstractSpringIntegrationLocalCILocalVCTest has hyperion enabled
class SpringAIAutoConfigurationInclusionsTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void testSpringAIAutoConfigurationsIncluded() {
        // When Hyperion is enabled, Spring AI autoconfigurations are NOT filtered out.
        // The AzureOpenAiChatModel bean should be created by the autoconfiguration.
        // We verify this by checking that at least one ChatModel bean exists in the context.
        assertThat(applicationContext.getBeanNamesForType(ChatModel.class)).isNotEmpty();
    }
}
