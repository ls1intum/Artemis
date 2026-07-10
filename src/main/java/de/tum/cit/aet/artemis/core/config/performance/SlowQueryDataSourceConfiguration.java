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
import org.springframework.context.annotation.Profile;

import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

/**
 * Spring configuration that wraps the auto-configured {@link DataSource} (HikariCP) with a
 * datasource-proxy {@link ProxyDataSource} when the {@code e2e-performance} profile is active.
 * <p>
 * The wrapping is done through a {@link BeanPostProcessor} so that:
 * <ul>
 * <li>Spring Boot's {@code DataSourceAutoConfiguration} still creates the HikariCP pool with
 * all of its normal configuration ({@code spring.datasource.*} properties, health
 * indicators, Actuator metrics, etc.).</li>
 * <li>The proxy transparently replaces the HikariCP bean in the application context without
 * requiring any changes to other beans or configuration files.</li>
 * </ul>
 * <p>
 * The inner {@link DataSourceProxyBeanPostProcessor} implements {@link ApplicationContextAware}
 * so it can resolve the {@link SlowQueryListener} lazily — only when the {@code "dataSource"} bean
 * is being post-processed. This avoids the early-initialisation problem that would occur if the
 * BPP tried to {@code @Autowire} a regular {@code @Component} at BPP instantiation time.
 * <p>
 * The {@code @Bean} factory method is {@code static} so Spring instantiates the processor
 * early (before {@code @Configuration} class post-processing), which is required for
 * {@link BeanPostProcessor} implementations.
 * <p>
 * Only active when the {@code e2e-performance} Spring profile is enabled.
 */
@Configuration
@Profile(SPRING_PROFILE_E2E_PERFORMANCE)
public class SlowQueryDataSourceConfiguration {

    /**
     * Registers the {@link DataSourceProxyBeanPostProcessor}.
     * Must be {@code static} so Spring can instantiate it before any {@code @Configuration}
     * class post-processing takes place.
     */
    @Bean
    public static DataSourceProxyBeanPostProcessor dataSourceProxyBeanPostProcessor() {
        return new DataSourceProxyBeanPostProcessor();
    }

    /**
     * A {@link BeanPostProcessor} that wraps the primary Spring Boot auto-configured
     * {@code DataSource} (bean name {@code "dataSource"}) with a datasource-proxy
     * {@link ProxyDataSource}.
     * <p>
     * The {@link SlowQueryListener} is obtained lazily from the {@link ApplicationContext}
     * at wrap time rather than at BPP instantiation time, ensuring all regular
     * {@code @Component} beans are fully initialised before they are first used.
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
            // Only wrap the primary DataSource; skip already-proxied instances
            // (e.g. Liquibase creates its own DataSource proxy internally).
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
