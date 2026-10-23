package de.tum.cit.aet.artemis.core.util.junit_extensions;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;

/** Installs submission tracking before the shared executor reaches any test-context consumer. */
@TestConfiguration
public class AsyncWorkTrackingConfiguration {

    /**
     * Decorates the initialized shared executor. Spring retains lifecycle ownership of the original bean, so the wrapper must not initialize or destroy it again.
     *
     * @return the processor that exposes tracked shared work
     */
    @Bean
    public static BeanPostProcessor trackSharedAsyncWork() {
        return new BeanPostProcessor() {

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if ("taskExecutor".equals(beanName) && bean instanceof AsyncTaskExecutor executor) {
                    return new TrackingAsyncTaskExecutor(executor);
                }
                return bean;
            }
        };
    }
}
