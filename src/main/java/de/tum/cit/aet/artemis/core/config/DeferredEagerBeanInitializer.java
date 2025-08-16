package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI_AND_LOCAL_DATA;

import java.util.Arrays;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.DeferredEagerBeanInitializationCompletedEvent;

/**
 * This component initializes all lazy singleton beans after the application is ready.
 * This allows us to benefit from the lazy initialization of beans during startup, without comprising end user experience as beans are initialized before the first request is
 * made.
 * We currently do not initialize the beans in parallel as it leads to weird issues with the classloader
 * The beans are initialized by accessing them, which triggers their initialization.
 */
@Component
@Profile({ PROFILE_CORE, PROFILE_BUILDAGENT })
@Lazy
public class DeferredEagerBeanInitializer {

    private static final Logger log = LoggerFactory.getLogger(DeferredEagerBeanInitializer.class);

    private final ConfigurableApplicationContext context;

    private final Environment env;

    public DeferredEagerBeanInitializer(ConfigurableApplicationContext context, Environment env) {
        this.context = context;
        this.env = env;
    }

    /**
     * Initializes all lazy singleton beans in the application context.
     * This method should be called after the application is fully started to not block the startup process.
     */
    public void initializeDeferredEagerBeans() {
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        if (!activeProfiles.contains("redis") && !activeProfiles.contains(PROFILE_LOCALCI_AND_LOCAL_DATA)) {
            try {
                // Force eager initialization of HazelcastConnection first, so that connections are established as early as possible.
                context.getBean(HazelcastConnection.class);
                log.debug("Priority initialization of HazelcastConnection completed");
            }
            catch (Throwable ex) {
                log.warn("Priority initialization of HazelcastConnection failed", ex);
            }
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
                log.warn("Deferred eager initialization of bean {} failed", name, ex);
            }
        });
        context.publishEvent(new DeferredEagerBeanInitializationCompletedEvent());
        log.info("Deferred eager initialization of all beans completed");
    }
}
