package de.tum.cit.aet.artemis.shared;

import static org.mockito.Mockito.spy;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import de.tum.cit.aet.artemis.globalsearch.service.IngestionBrowserService;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionBrowserWeaviateReadService;

/**
 * Provides browser service spies in the shared LocalCI/LocalVC contexts, without creating a context per test class.
 * These services do not exist when Weaviate is disabled, so only wrap beans that the normal configuration creates.
 */
@TestConfiguration(proxyBeanMethods = false)
public class IngestionBrowserTestConfiguration {

    @Bean
    static BeanPostProcessor ingestionBrowserServiceSpies() {
        return new BeanPostProcessor() {

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof IngestionBrowserService || bean instanceof IngestionBrowserWeaviateReadService ? spy(bean) : bean;
            }
        };
    }
}
