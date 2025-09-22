package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.PlaceholderResolutionException;

import de.tum.cit.aet.artemis.core.DeferredEagerBeanInitializationCompletedEvent;

/**
 * This component initializes all lazy singleton beans after the application is ready.
 * This allows us to benefit from the lazy initialization of beans during startup, without compromising end user experience as beans are initialized before the first request is
 * made.
 * We currently do not initialize the beans in parallel as it leads to weird issues with the classloader
 * The beans are initialized by accessing them, which triggers their initialization.
 */
@Component
@Profile(PROFILE_CORE)
@Lazy
public class DeferredEagerBeanInitializer {

    private static final Logger log = LoggerFactory.getLogger(DeferredEagerBeanInitializer.class);

    private final ConfigurableApplicationContext context;

    public DeferredEagerBeanInitializer(ConfigurableApplicationContext context) {
        this.context = context;
    }

    /**
     * Initializes all lazy singleton beans in the application context.
     * This method should be called after the application is fully started to not block the startup process.
     */
    public void initializeDeferredEagerBeans() {
        log.info("Start deferred eager initialization of all lazy singleton beans");
        // Force eager initialization of HazelcastConnection first, so that connections are established as early as possible.
        try {
            context.getBean(HazelcastConnection.class);
            log.debug("Priority initialization of HazelcastConnection completed");
        }
        catch (Throwable ex) {
            shutdownOnDeferredInitFailure("HazelcastConnection", ex);
        }

        DefaultListableBeanFactory bf = (DefaultListableBeanFactory) context.getBeanFactory();
        Arrays.stream(bf.getBeanDefinitionNames()).filter(name -> {
            BeanDefinition def = bf.getBeanDefinition(name);
            return def.isSingleton() && def.isLazyInit();
        }).forEach(name -> {
            try {
                // accessing the bean triggers initialization
                context.getBean(name);
                log.debug("Deferred eager initialization of bean {} completed", name);
            }
            catch (Throwable ex) {
                shutdownOnDeferredInitFailure(name, ex);
            }
        });
        context.publishEvent(new DeferredEagerBeanInitializationCompletedEvent());
        log.info("Deferred eager initialization of all beans completed");
    }

    private void shutdownOnDeferredInitFailure(String name, Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root instanceof PlaceholderResolutionException) {
            log.error("Required configuration is missing while initializing bean {}: {}", name, root.getMessage());
        }
        else if (root instanceof NoSuchBeanDefinitionException) {
            log.error("A required dependency for bean {} is missing: {}", name, root.getMessage());
        }
        log.error("Deferred eager initialization of bean {} failed. Shutting down the application.", name, ex);
        System.exit(SpringApplication.exit(context, () -> 2));
    }
}
