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
 * Conditionally adds HikariCP connection pooling to the test DataSource provided by Zonky.
 * <p>
 * <b>Problem:</b> Zonky's {@code @AutoConfigureEmbeddedDatabase} replaces Spring Boot's auto-configured
 * HikariCP DataSource with its own unpooled implementation. For Testcontainers-backed databases
 * (PostgreSQL, MySQL), every {@code getConnection()} call creates a new TCP socket. Over thousands
 * of tests, these sockets accumulate in TIME_WAIT state and exhaust the operating system's ephemeral
 * port range (~16K ports on macOS), causing {@code java.net.BindException: Can't assign requested address}.
 * <p>
 * <b>Solution:</b> This configuration wraps Zonky's DataSource in HikariCP so TCP connections are pooled
 * and reused, preventing port exhaustion and improving test performance.
 * <p>
 * <b>H2 exclusion:</b> H2 runs in-process (no TCP sockets), so pooling is unnecessary. Wrapping H2 with
 * HikariCP actually causes {@link OutOfMemoryError} in large test suites because the pooled H2 sessions
 * live in the same JVM heap and accumulate memory that is not released between tests. H2 DataSources are
 * therefore returned unwrapped.
 * <p>
 * <b>PostgreSQL timezone fix:</b> For PostgreSQL, a {@code SET TIME ZONE 'UTC'} connection init SQL is
 * configured to prevent timezone shift issues. PostgreSQL's {@code TIMESTAMP WITHOUT TIME ZONE} columns
 * can shift values by the JVM's timezone offset during Hibernate round-trips, particularly for fields
 * mapped via {@code @SecondaryTable}. Setting the session timezone to UTC at the JDBC level prevents this,
 * even though {@code hibernate.jdbc.time_zone: UTC} is already set in application.yml (Hibernate does not
 * consistently apply this setting for secondary table columns).
 *
 * @see PostgresTmpfsContainerCustomizer
 */
@Configuration
@Lazy
public class TestDataSourcePoolConfig {

    private static final Logger log = LoggerFactory.getLogger(TestDataSourcePoolConfig.class);

    /**
     * Detects the database type by querying JDBC metadata. This is called once during Spring context
     * initialization to determine whether HikariCP pooling and database-specific settings are needed.
     */
    private static String getDatabaseProductName(DataSource ds) {
        try (var conn = ds.getConnection()) {
            return conn.getMetaData().getDatabaseProductName().toLowerCase();
        }
        catch (SQLException e) {
            log.warn("Could not determine database type", e);
            return "";
        }
    }

    @Bean
    static BeanPostProcessor dataSourcePoolingPostProcessor() {
        return new BeanPostProcessor() {

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if ("dataSource".equals(beanName) && bean instanceof DataSource ds && !(bean instanceof HikariDataSource)) {
                    String dbProduct = getDatabaseProductName(ds);

                    // H2 is in-process and does not use TCP sockets, so pooling is unnecessary.
                    // Wrapping H2 with HikariCP causes memory overhead (sessions live in-JVM)
                    // that leads to OOM in large test suites.
                    if (dbProduct.contains("h2")) {
                        log.info("Skipping HikariCP wrapping for in-process H2 DataSource");
                        return bean;
                    }

                    log.info("Wrapping '{}' DataSource ({}) with HikariCP connection pool", beanName, bean.getClass().getSimpleName());
                    HikariDataSource pooled = new HikariDataSource();
                    pooled.setDataSource(ds);
                    pooled.setMaximumPoolSize(20);
                    pooled.setMinimumIdle(10);
                    // autoCommit=false is required: Artemis integration tests do not use @Transactional,
                    // so Hibernate manages transactions itself and expects connections with autoCommit off.
                    pooled.setAutoCommit(false);
                    // maxLifetime=0 disables connection cycling. Test containers have a stable lifecycle,
                    // so connections can be reused indefinitely without risk of stale TCP connections.
                    pooled.setMaxLifetime(0);
                    pooled.setConnectionTimeout(30000);
                    pooled.setPoolName("TestPool");

                    // Set connection init SQL to ensure consistent timezone handling.
                    // PostgreSQL's TIMESTAMP WITHOUT TIME ZONE can shift values by the JVM
                    // timezone offset during Hibernate round-trips (especially for @SecondaryTable
                    // columns). Setting the session timezone to UTC prevents this.
                    if (dbProduct.contains("postgres")) {
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
