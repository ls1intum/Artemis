package de.tum.cit.aet.artemis.shared.config;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiChatAutoConfiguration;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;

// AbstractSpringIntegrationJenkinsLocalVCTest has hyperion disabled
class SpringAIAutoConfigurationExclusionsTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void testSpringAIAutoConfigurationsExcluded() {
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> applicationContext.getBean(AzureOpenAiChatAutoConfiguration.class));
    }
}
