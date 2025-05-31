package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * This component initializes all lazy singleton beans after the application is ready.
 * This allows us to benefit from the lazy initialization of beans during startup, without comprising end user experience as beans are initialized before the first request is
 * made.
 * It uses parallel streams to initialize the beans in parallel.
 * The beans are initialized by accessing them, which triggers their initialization.
 */
@Component
@Profile({ PROFILE_CORE, PROFILE_BUILDAGENT })
@Lazy
public class DeferredEagerBeanInitializer {

    private static final Logger log = LoggerFactory.getLogger(DeferredEagerBeanInitializer.class);

    private final ConfigurableApplicationContext context;

    public DeferredEagerBeanInitializer(ConfigurableApplicationContext context) {
        this.context = context;
    }

    public void initializeDeferredEagerBeans() {
        DefaultListableBeanFactory bf = (DefaultListableBeanFactory) context.getBeanFactory();
        Arrays.stream(bf.getBeanDefinitionNames()).parallel().filter(name -> {
            BeanDefinition def = bf.getBeanDefinition(name);
            return def.isSingleton() && def.isLazyInit();
        }).forEach(name -> {
            try {
                // accessing the bean triggers initialization
                context.getBean(name);
                log.debug("Deferred eager initialization of bean {} completed", name);
            }
            catch (Throwable ex) {
                log.warn("Deferred eager initialization of bean {} failed", name, ex);
            }
        });

        log.info("Deferred eager initialization of all beans completed");
    }
}
