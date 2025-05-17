package de.tum.cit.aet.artemis.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class EagerBeanLogger implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(EagerBeanLogger.class);

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        applicationContext.addBeanFactoryPostProcessor(beanFactory -> {
            for (String name : beanFactory.getBeanDefinitionNames()) {
                var def = beanFactory.getBeanDefinition(name);
                if (!def.isLazyInit()) {
                    log.debug("Bean was created eagerly and not lazily: {} - {}", name, def.getBeanClassName());
                }
            }
        });
    }
}
