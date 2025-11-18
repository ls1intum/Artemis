package de.tum.cit.aet.artemis.shared.config;

import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

// AbstractSpringIntegrationLocalCILocalVCTest has hyperion enabled
class SpringAIAutoConfigurationInclusionsTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void testSpringAIAutoConfigurationsIncluded() {
        assertThatNoException().isThrownBy(() -> applicationContext.getBean(OpenAiChatAutoConfiguration.class));
    }
}
