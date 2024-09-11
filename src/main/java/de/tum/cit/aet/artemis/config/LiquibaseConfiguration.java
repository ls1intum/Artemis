package de.tum.cit.aet.artemis.config;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import de.tum.cit.aet.artemis.config.migration.DatabaseMigration;
import liquibase.Scope;
import liquibase.SingletonScopeManager;
import liquibase.integration.spring.SpringLiquibase;
import tech.jhipster.config.JHipsterConstants;
import tech.jhipster.config.liquibase.SpringLiquibaseUtil;

@Profile(PROFILE_CORE)
@Configuration
public class LiquibaseConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LiquibaseConfiguration.class);

    private final Environment env;

    private final BuildProperties buildProperties;

    private DataSource dataSource;

    private DatabaseMigration databaseMigration;

    private String currentVersionString;

    public LiquibaseConfiguration(Environment env, BuildProperties buildProperties) {
        this.env = env;
        this.buildProperties = buildProperties;
    }

    /**
     * reads properties and configures liquibase
     *
     * @param liquibaseDataSource      the liquibase sql data source
     * @param liquibaseProperties      the liquibase properties
     * @param dataSourceObjectProvider the sql data source
     * @param dataSourceProperties     data source properties
     * @return the configured spring liquibase object
     */
    @Bean
    public SpringLiquibase liquibase(@LiquibaseDataSource ObjectProvider<DataSource> liquibaseDataSource, LiquibaseProperties liquibaseProperties,
            ObjectProvider<DataSource> dataSourceObjectProvider, DataSourceProperties dataSourceProperties) {

        this.dataSource = dataSourceObjectProvider.getIfUnique();
        this.currentVersionString = buildProperties.getVersion();

        if (!env.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            this.databaseMigration = new DatabaseMigration(currentVersionString, dataSource);
            databaseMigration.checkMigrationPath();
        }

        SpringLiquibase liquibase = SpringLiquibaseUtil.createSpringLiquibase(liquibaseDataSource.getIfAvailable(), liquibaseProperties, dataSource, dataSourceProperties);
        Scope.setScopeManager(new SingletonScopeManager());
        liquibase.setChangeLog("classpath:config/liquibase/master.xml");
        liquibase.setContexts(liquibaseProperties.getContexts());
        liquibase.setDefaultSchema(liquibaseProperties.getDefaultSchema());
        liquibase.setLiquibaseSchema(liquibaseProperties.getLiquibaseSchema());
        liquibase.setLiquibaseTablespace(liquibaseProperties.getLiquibaseTablespace());
        liquibase.setDatabaseChangeLogLockTable(liquibaseProperties.getDatabaseChangeLogLockTable());
        liquibase.setDatabaseChangeLogTable(liquibaseProperties.getDatabaseChangeLogTable());
        liquibase.setDropFirst(liquibaseProperties.isDropFirst());
        liquibase.setLabelFilter(liquibaseProperties.getLabelFilter());
        liquibase.setChangeLogParameters(liquibaseProperties.getParameters());
        liquibase.setRollbackFile(liquibaseProperties.getRollbackFile());
        liquibase.setTestRollbackOnUpdate(liquibaseProperties.isTestRollbackOnUpdate());
        if (env.acceptsProfiles(Profiles.of(JHipsterConstants.SPRING_PROFILE_NO_LIQUIBASE))) {
            liquibase.setShouldRun(false);
            log.info("Liquibase is disabled");
        }
        else {
            liquibase.setShouldRun(liquibaseProperties.isEnabled());
            log.info("Liquibase is enabled");
        }
        return liquibase;
    }

    private Statement createStatement() throws SQLException {
        var connection = dataSource.getConnection();
        return connection.createStatement();
    }

    /**
     * Stores the current version of the application in the database. This method is triggered
     * after the application is fully started, as indicated by the {@link ApplicationReadyEvent}.
     * It checks if the application is running under the test profile to avoid updating the version
     * in test environments. If not in a test environment, it either inserts the current version into
     * the database if it's the first run, or updates the existing version entry otherwise.
     * <p>
     * This operation ensures that the application's version is tracked in the database, allowing
     * for future reference and potential migration checks.
     *
     * @param event The {@link ApplicationReadyEvent} containing the application context, used to retrieve
     *                  the environment and determine if the application is running with specific profiles.
     */
    @EventListener
    public void storeCurrentVersionToDatabase(ApplicationReadyEvent event) {
        if (event.getApplicationContext().getEnvironment().acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return; // Do not perform any operations if the application is running in the test profile.
        }

        String sqlStatement = this.databaseMigration.getPreviousVersionString() == null ? "INSERT INTO artemis_version (latest_version) VALUES(?);"
                : "UPDATE artemis_version SET latest_version = ?;";

        try (var connection = dataSource.getConnection(); var preparedStatement = connection.prepareStatement(sqlStatement)) {
            preparedStatement.setString(1, currentVersionString);

            // Logging the action based on whether it's an insert or an update.
            if (this.databaseMigration.getPreviousVersionString() == null) {
                log.info("Inserting latest version {} into database", currentVersionString);
            }
            else {
                log.info("Updating latest version to {} in database", currentVersionString);
            }

            preparedStatement.executeUpdate();
            connection.commit(); // Ensure the transaction is committed.
        }
        catch (SQLException e) {
            log.error("Failed to store the current version to the database", e);
            throw new RuntimeException("Error updating the application version in the database", e);
        }
    }

}
