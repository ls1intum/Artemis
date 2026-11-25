package de.tum.cit.aet.artemis.core.config.performance;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_E2E_PERFORMANCE;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

/**
 * Wraps the auto-configured {@link DataSource} with a datasource-proxy {@link ProxyDataSource} that reports every
 * statement to the {@link SlowQueryListener}, when the {@code e2e-performance} profile is active. Done in a
 * {@link BeanPostProcessor}, so Spring Boot still creates the connection pool with all its normal configuration.
 */
@Configuration
@Profile(SPRING_PROFILE_E2E_PERFORMANCE)
@Lazy
public class SlowQueryDataSourceConfiguration {

    /**
     * Static, so Spring can instantiate the post-processor before any {@code @Configuration} class is processed.
     *
     * @return the post-processor that wraps the {@code DataSource}.
     */
    @Bean
    public static DataSourceProxyBeanPostProcessor dataSourceProxyBeanPostProcessor() {
        return new DataSourceProxyBeanPostProcessor();
    }

    /**
     * Wraps the primary {@code DataSource} (bean name {@code "dataSource"}). The {@link SlowQueryListener} is looked
     * up at wrap time, not injected, because a post-processor is created before regular components exist.
     */
    static class DataSourceProxyBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {

        private static final Logger log = LoggerFactory.getLogger(DataSourceProxyBeanPostProcessor.class);

        private ApplicationContext applicationContext;

        @Override
        public void setApplicationContext(ApplicationContext ctx) throws BeansException {
            this.applicationContext = ctx;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof DataSource dataSource && !(bean instanceof ProxyDataSource) && "dataSource".equals(beanName)) {
                SlowQueryListener listener = applicationContext.getBean(SlowQueryListener.class);
                ProxyDataSource proxy = ProxyDataSourceBuilder.create(dataSource).name("artemis-slow-query-proxy").listener(listener).build();

                log.info("[SlowQuery] DataSource wrapped with datasource-proxy (slow-query detection active)");
                return proxy;
            }
            return bean;
        }
    }
}
