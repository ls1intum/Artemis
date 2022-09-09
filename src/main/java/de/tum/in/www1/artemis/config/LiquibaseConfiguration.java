package de.tum.in.www1.artemis.config;

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
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import com.vdurmont.semver4j.Semver;

import liquibase.integration.spring.SpringLiquibase;
import tech.jhipster.config.JHipsterConstants;
import tech.jhipster.config.liquibase.SpringLiquibaseUtil;

@Configuration
public class LiquibaseConfiguration {

    private final Logger log = LoggerFactory.getLogger(LiquibaseConfiguration.class);

    private final Environment env;

    private final BuildProperties buildProperties;

    private DataSource dataSource;

    private String currentVersionString;

    private String previousVersionString;

    public LiquibaseConfiguration(Environment env, BuildProperties buildProperties) {
        this.env = env;
        this.buildProperties = buildProperties;
    }

    /**
     * reads properties and configures liquibase
     *
     * @param liquibaseDataSource the liquibase sql data source
     * @param liquibaseProperties the liquibase properties
     * @param dataSourceObjectProvider the sql data source
     * @param dataSourceProperties data source properties
     * @return the configured spring liquibase object
     */
    @Bean
    public SpringLiquibase liquibase(@LiquibaseDataSource ObjectProvider<DataSource> liquibaseDataSource, LiquibaseProperties liquibaseProperties,
            ObjectProvider<DataSource> dataSourceObjectProvider, DataSourceProperties dataSourceProperties) {

        this.dataSource = dataSourceObjectProvider.getIfUnique();
        this.currentVersionString = buildProperties.getVersion();

        if (!env.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            checkMigrationPath();
        }

        SpringLiquibase liquibase = SpringLiquibaseUtil.createSpringLiquibase(liquibaseDataSource.getIfAvailable(), liquibaseProperties, dataSource, dataSourceProperties);
        liquibase.setChangeLog("classpath:config/liquibase/master.xml");
        liquibase.setContexts(liquibaseProperties.getContexts());
        liquibase.setDefaultSchema(liquibaseProperties.getDefaultSchema());
        liquibase.setLiquibaseSchema(liquibaseProperties.getLiquibaseSchema());
        liquibase.setLiquibaseTablespace(liquibaseProperties.getLiquibaseTablespace());
        liquibase.setDatabaseChangeLogLockTable(liquibaseProperties.getDatabaseChangeLogLockTable());
        liquibase.setDatabaseChangeLogTable(liquibaseProperties.getDatabaseChangeLogTable());
        liquibase.setDropFirst(liquibaseProperties.isDropFirst());
        liquibase.setLabels(liquibaseProperties.getLabels());
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

    String migrationPathVersion5_10_3_String = "5.10.3";

    private void checkMigrationPath() {
        var currentVersion = new Semver(currentVersionString);
        var migrationPathVersion = new Semver(migrationPathVersion5_10_3_String);
        var version600 = new Semver("6.0.0");
        var version700 = new Semver("7.0.0");
        if (currentVersion.isLowerThan(version600)) {
            log.info("Migration path check: Not necessary");
        }
        if (currentVersion.isGreaterThanOrEqualTo(version600) && currentVersion.isLowerThan(version700)) {
            previousVersionString = getPreviousVersionElseThrow();
            log.info("The previous version was " + previousVersionString);
            if (previousVersionString == null) {
                // this means Artemis was never started before and no DATABASECHANGELOG exists, we can simply proceed
                return;
            }
            var previousVersion = new Semver(previousVersionString);
            if (previousVersion.isLowerThan(migrationPathVersion)) {
                log.error("Cannot start Artemis. Please start the release {} first, otherwise the migration will fail", migrationPathVersion5_10_3_String);
            }
            else if (previousVersion.isEqualTo(migrationPathVersion)) {
                // this means this is the first start after the mandatory previous update, we need to set the checksum of the initial schema to null
                // TODO: for some reason this does not work and leads to a timeout exception
                updateInitialChecksum();
            }
        }

    }

    private String getPreviousVersionElseThrow() {
        String error = "Cannot start Artemis because version table does not exist, but a migration path is necessary! Please start the release " + migrationPathVersion5_10_3_String
                + " first, otherwise the migration will fail";
        try (var statement = createStatement()) {
            statement.executeQuery("SELECT * FROM DATABASECHANGELOG");
            var result = statement.executeQuery("SELECT latest_version FROM artemis_version");
            statement.closeOnCompletion();
            if (result.next()) {
                return result.getString("latest_version");
            }
            // if no version exists, we fail here
            log.error(error);
            throw new RuntimeException(error);
        }
        catch (SQLException e) {
            if (e.getMessage().contains("databasechangelog") && (e.getMessage().contains("does not exist") || (e.getMessage().contains("doesn't exist")))) {
                return null;
            }
            log.error(error);
            throw new RuntimeException(error, e);
        }
    }

    private void updateInitialChecksum() {
        try (var statement = createStatement()) {
            log.info("Set checksum of initial schema to null so that liquibase will recalculate it");
            statement.executeUpdate(
                    "UPDATE DATABASECHANGELOG SET MD5SUM = null, DATEEXECUTED = now(), DESCRIPTION = 'Initial schema generation for version 6.0.0', LIQUIBASE = '4.15.0', FILENAME = 'config/liquibase/changelog/00000000000000_initial_schema.xml' WHERE ID = '00000000000001';");
            statement.getConnection().commit();
            statement.closeOnCompletion();
        }
        catch (SQLException e) {
            log.error("Cannot update checksum for initial schema migration", e);
            throw new RuntimeException(e);
        }
    }

    private Statement createStatement() throws SQLException {
        var connection = dataSource.getConnection();
        return connection.createStatement();
    }

    @EventListener()
    public void storeCurrentVersionToDatabase(ApplicationReadyEvent event) {
        if (event.getApplicationContext().getEnvironment().acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return;
        }
        try (var statement = createStatement()) {
            if (previousVersionString == null) {
                log.info("Insert latest version " + currentVersionString + " into database");
                statement.executeUpdate("INSERT INTO artemis_version (latest_version) VALUES('" + currentVersionString + "')");
            }
            else {
                log.info("Update latest version to " + currentVersionString + " in database");
                statement.executeUpdate("UPDATE artemis_version SET latest_version = '" + currentVersionString + "'");
            }
            statement.getConnection().commit();
            statement.closeOnCompletion();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
