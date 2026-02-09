package de.tum.cit.aet.artemis.shared.config;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Adds connection pooling to the test DataSource provided by Zonky.
 * <p>
 * Zonky's {@code @AutoConfigureEmbeddedDatabase} replaces Spring Boot's auto-configured
 * HikariCP DataSource with its own unpooled implementation. For in-process databases (H2),
 * this is acceptable since connections don't use TCP sockets. However, for Testcontainers-backed
 * databases (PostgreSQL, MySQL), every {@code getConnection()} call creates a new TCP socket.
 * <p>
 * Over thousands of tests, these sockets accumulate in TIME_WAIT state and can exhaust the
 * operating system's ephemeral port range (~16K ports on macOS), causing
 * {@code java.net.BindException: Can't assign requested address}.
 * <p>
 * This configuration wraps Zonky's DataSource in HikariCP so TCP connections are pooled
 * and reused, preventing port exhaustion and also improving test performance.
 */
@Configuration
@Lazy
public class TestDataSourcePoolConfig {

    private static final Logger log = LoggerFactory.getLogger(TestDataSourcePoolConfig.class);

    private static boolean isPostgres(DataSource ds) {
        try (var conn = ds.getConnection()) {
            return conn.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        }
        catch (SQLException e) {
            log.warn("Could not determine database type for timezone configuration", e);
            return false;
        }
    }

    @Bean
    static BeanPostProcessor dataSourcePoolingPostProcessor() {
        return new BeanPostProcessor() {

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if ("dataSource".equals(beanName) && bean instanceof DataSource ds && !(bean instanceof HikariDataSource)) {
                    log.info("Wrapping '{}' DataSource ({}) with HikariCP connection pool", beanName, bean.getClass().getSimpleName());
                    HikariDataSource pooled = new HikariDataSource();
                    pooled.setDataSource(ds);
                    pooled.setMaximumPoolSize(20);
                    pooled.setMinimumIdle(10);
                    pooled.setMaxLifetime(0);
                    pooled.setConnectionTimeout(30000);
                    pooled.setPoolName("TestPool");

                    // Set connection init SQL to ensure consistent timezone handling.
                    // PostgreSQL's TIMESTAMP WITHOUT TIME ZONE can shift values by the JVM
                    // timezone offset during Hibernate round-trips (especially for @SecondaryTable
                    // columns). Setting the session timezone to UTC prevents this.
                    if (isPostgres(ds)) {
                        pooled.setConnectionInitSql("SET TIME ZONE 'UTC'");
                        log.info("Configured PostgreSQL connection init SQL: SET TIME ZONE 'UTC'");
                    }

                    return pooled;
                }
                return bean;
            }
        };
    }
}
