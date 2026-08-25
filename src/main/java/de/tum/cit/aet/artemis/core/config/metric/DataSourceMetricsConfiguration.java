package de.tum.cit.aet.artemis.core.config.metric;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.jdbc.metadata.DataSourcePoolMetadataProvider;
import org.springframework.boot.jdbc.metadata.HikariDataSourcePoolMetadata;
import org.springframework.boot.jdbc.metrics.DataSourcePoolMetrics;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Wires HikariCP connection pool metrics into Micrometer.
 *
 * <p>
 * This used to live on the Hazelcast configuration, which meant the database pool stopped reporting as soon as a
 * deployment selected a different distributed data provider. Pool metrics have nothing to do with the provider, so they
 * are configured on their own here.
 *
 * <p>
 * Core-only, because build agents deliberately run without a datasource (see {@code application-buildagent.yml}, which
 * blanks the datasource URL and excludes {@code DataSourcePoolMetricsAutoConfiguration}).
 */
@Profile(PROFILE_CORE)
@Lazy(value = false)
@Configuration
public class DataSourceMetricsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DataSourceMetricsConfiguration.class);

    private final ApplicationContext applicationContext;

    private final Optional<MeterRegistry> meterRegistry;

    public DataSourceMetricsConfiguration(ApplicationContext applicationContext, Optional<MeterRegistry> meterRegistry) {
        this.applicationContext = applicationContext;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Configures HikariCP with {@link MicrometerMetricsTrackerFactory} before the connection pool starts.
     * <p>
     * HikariCP timer metrics (acquire, creation, usage) are only tracked when a {@code MetricsTrackerFactory}
     * is set before the pool's first {@code getConnection()} call. Spring Boot's auto-configuration
     * ({@code DataSourcePoolMetricsAutoConfiguration}) attempts to set it via a {@code MeterBinder},
     * but that runs after JPA/Hibernate initialization has already started the pool.
     * <p>
     * This {@code BeanPostProcessor} runs {@code postProcessBeforeInitialization} which executes
     * after the HikariDataSource constructor but before any dependent bean (like EntityManagerFactory)
     * can call {@code getConnection()}, ensuring the metrics tracker is configured in time.
     *
     * @param meterRegistryProvider lazy provider to avoid circular dependency during early initialization
     * @return the BeanPostProcessor (must be static to prevent early initialization of enclosing config)
     */
    @Bean
    static BeanPostProcessor hikariMetricsPostProcessor(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new BeanPostProcessor() {

            private static final Logger log = LoggerFactory.getLogger("HikariMetricsPostProcessor");

            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof HikariDataSource hikari && hikari.getMetricsTrackerFactory() == null) {
                    MeterRegistry registry = meterRegistryProvider.getIfAvailable();
                    if (registry != null) {
                        hikari.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(registry));
                        log.info("Configured HikariCP with MicrometerMetricsTrackerFactory for timer metrics (acquire, creation, usage)");
                    }
                }
                return bean;
            }
        };
    }

    /**
     * Binds HikariCP pool gauges (active, idle, min, max, pending connections) after the application is fully started.
     * Timer metrics (acquire, creation, usage) are registered by {@link #hikariMetricsPostProcessor}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void bindDataSourceMetricsToMicrometer() {
        if (meterRegistry.isEmpty()) {
            return;
        }
        try {
            var dataSource = applicationContext.getBean(DataSource.class);
            if (dataSource instanceof HikariDataSource hikariDataSource) {
                DataSourcePoolMetadataProvider provider = ds -> new HikariDataSourcePoolMetadata((HikariDataSource) ds);
                new DataSourcePoolMetrics(dataSource, provider, "hikaricp", List.of()).bindTo(meterRegistry.get());
                log.info("Bound HikariCP pool metrics to Micrometer for pool '{}'", hikariDataSource.getPoolName());
            }
        }
        catch (Exception e) {
            log.warn("Could not bind datasource pool metrics: {}", e.getMessage());
        }
    }
}
