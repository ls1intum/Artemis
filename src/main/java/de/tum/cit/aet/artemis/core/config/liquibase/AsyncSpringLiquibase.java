package de.tum.cit.aet.artemis.core.config.liquibase;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executor;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import de.tum.cit.aet.artemis.core.config.ArtemisConstants;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;

/**
 * Inlined replacement for JHipster's {@code AsyncSpringLiquibase} and {@code SpringLiquibaseUtil}.
 * <p>
 * Provides utility methods to create a properly configured {@link SpringLiquibase} instance,
 * and optionally run Liquibase migrations asynchronously in the development profile.
 */
public class AsyncSpringLiquibase extends SpringLiquibase {

    private static final Logger log = LoggerFactory.getLogger(AsyncSpringLiquibase.class);

    private static final long SLOWNESS_THRESHOLD = 5; // seconds

    private final Executor asyncExecutor;

    private final Environment env;

    public AsyncSpringLiquibase(Executor asyncExecutor, Environment env) {
        this.asyncExecutor = asyncExecutor;
        this.env = env;
    }

    @Override
    public void afterPropertiesSet() throws LiquibaseException {
        if (isAsyncProfileActive()) {
            asyncExecutor.execute(() -> {
                try {
                    log.warn("Starting Liquibase asynchronously. Your database might not be ready at startup!");
                    initDb();
                }
                catch (LiquibaseException e) {
                    log.error("Liquibase could not start correctly, your database is not ready: {}", e.getMessage(), e);
                }
            });
        }
        else {
            log.debug("Starting Liquibase synchronously");
            initDb();
        }
    }

    private boolean isAsyncProfileActive() {
        return env.acceptsProfiles(Profiles.of(ArtemisConstants.SPRING_PROFILE_DEVELOPMENT + " & !" + ArtemisConstants.SPRING_PROFILE_NO_LIQUIBASE));
    }

    private void initDb() throws LiquibaseException {
        long startTime = System.currentTimeMillis();
        super.afterPropertiesSet();
        long duration = (System.currentTimeMillis() - startTime) / 1000;
        if (duration > SLOWNESS_THRESHOLD) {
            log.warn("Liquibase took {} seconds to start", duration);
        }
        else {
            log.debug("Liquibase has updated your database in {} seconds", duration);
        }
    }

    /**
     * Creates a {@link SpringLiquibase} instance with the correct DataSource configuration.
     * <p>
     * If a dedicated Liquibase DataSource is provided, it is used directly.
     * Otherwise, if the standard DataSource is available, it is used.
     * As a fallback, a new DataSource is created from the provided properties.
     *
     * @param liquibaseDataSource  optional dedicated Liquibase DataSource
     * @param liquibaseProperties  Liquibase-specific properties (URL, user, password)
     * @param dataSource           the primary application DataSource
     * @param dataSourceProperties the DataSource configuration properties
     * @return a configured {@link SpringLiquibase} instance
     */
    public static SpringLiquibase createSpringLiquibase(DataSource liquibaseDataSource, LiquibaseProperties liquibaseProperties, DataSource dataSource,
            DataSourceProperties dataSourceProperties) {

        SpringLiquibase liquibase = new SpringLiquibase();

        if (liquibaseDataSource != null) {
            liquibase.setDataSource(liquibaseDataSource);
        }
        else if (liquibaseProperties.getUrl() != null) {
            liquibase.setDataSource(createNewDataSource(liquibaseProperties, dataSourceProperties));
        }
        else {
            liquibase.setDataSource(dataSource);
        }

        return liquibase;
    }

    private static DataSource createNewDataSource(LiquibaseProperties liquibaseProperties, DataSourceProperties dataSourceProperties) {
        String url = liquibaseProperties.getUrl();
        String user = liquibaseProperties.getUser() != null ? liquibaseProperties.getUser() : dataSourceProperties.getUsername();
        String password = liquibaseProperties.getPassword() != null ? liquibaseProperties.getPassword() : dataSourceProperties.getPassword();

        return DataSourceBuilder.create().url(url).username(user).password(password).build();
    }

    /**
     * Checks whether the underlying DataSource connection is valid.
     *
     * @param dataSource the DataSource to check
     * @return true if a connection can be obtained, false otherwise
     */
    public static boolean isConnectionValid(DataSource dataSource) {
        try (Connection ignored = dataSource.getConnection()) {
            return true;
        }
        catch (SQLException e) {
            log.warn("Could not establish database connection for Liquibase: {}", e.getMessage());
            return false;
        }
    }
}
