package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * This component initializes all lazy singleton beans after the application is ready.
 * It uses a thread pool to initialize the beans in parallel, which can help reduce startup time.
 * The beans are initialized by accessing them, which triggers their initialization.
 */
@Component
@Profile({ PROFILE_CORE, PROFILE_BUILDAGENT })
@Lazy
public class DeferredEagerBeanInitializer {

    private static final Logger log = LoggerFactory.getLogger(DeferredEagerBeanInitializer.class);

    private final ConfigurableApplicationContext context;

    private final ThreadPoolTaskExecutor executor;

    public DeferredEagerBeanInitializer(ConfigurableApplicationContext context, ThreadPoolTaskExecutor deferredEagerInitializationTaskExecutor) {
        this.context = context;
        this.executor = deferredEagerInitializationTaskExecutor;
    }

    public void initializeDeferredEagerBeans() {
        DefaultListableBeanFactory bf = (DefaultListableBeanFactory) context.getBeanFactory();

        for (String name : bf.getBeanDefinitionNames()) {
            BeanDefinition def = bf.getBeanDefinition(name);
            if (def.getRole() == BeanDefinition.ROLE_APPLICATION && def.isSingleton() && def.isLazyInit()) {

                executor.submit(() -> {
                    try {
                        // accessing a bean will trigger its initialization
                        context.getBean(name);
                        log.debug("Deferred eager initialization of bean {} completed", name);
                    }
                    catch (Throwable ex) {
                        log.warn("Deferred eager initialization of bean {} failed", name, ex);
                    }
                });
            }
        }
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.shutdown();
        log.info("DeferredBeanPreloader executor shutdown after submitting all tasks");
    }
}
