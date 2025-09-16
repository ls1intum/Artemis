package de.tum.cit.aet.artemis.shared.config;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;

// AbstractSpringIntegrationJenkinsLocalVCTest has hyperion disabled
class HyperionAutoConfigurationExclusionsTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void testSpringAiAutoConfigurationsExcluded() {
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class).isThrownBy(() -> applicationContext.getBean(AzureOpenAiChatModel.class));
    }
}
