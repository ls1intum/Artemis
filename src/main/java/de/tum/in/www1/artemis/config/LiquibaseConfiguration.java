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
        SpringLiquibase liquibase = SpringLiquibaseUtil.createSpringLiquibase(liquibaseDataSource.getIfAvailable(), liquibaseProperties, dataSourceObjectProvider.getIfUnique(),
                dataSourceProperties);

        if (!env.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            dataSource = dataSourceObjectProvider.getIfUnique();
            currentVersionString = buildProperties.getVersion();
            previousVersionString = getPreviousVersionElseThrow();
            log.info("The previous version was {}", previousVersionString);
        }

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

    private String getPreviousVersionElseThrow() {
        try (var statement = createStatement()) {
            statement.executeQuery("SELECT * FROM DATABASECHANGELOG;");
            var result = statement.executeQuery("SELECT latest_version FROM artemis_version;");
            statement.closeOnCompletion();
            if (result.next()) {
                return result.getString("latest_version");
            }
            return null;
        }
        catch (SQLException e) {
            log.info(e.getMessage());
            // if no changelog or no version cane be found, it means it is not yet available, we do not need to throw an exception
            return null;
        }
    }

    private Statement createStatement() throws SQLException {
        var connection = dataSource.getConnection();
        return connection.createStatement();
    }

    /**
     * Stores the current version in the database after the application is ready
     * @param event used to prevent this method for running in tests
     */
    @EventListener()
    public void storeCurrentVersionToDatabase(ApplicationReadyEvent event) {
        if (event.getApplicationContext().getEnvironment().acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return;
        }
        try (var statement = createStatement()) {
            if (previousVersionString == null) {
                log.info("Insert latest version {} into database", currentVersionString);
                statement.executeUpdate("INSERT INTO artemis_version (latest_version) VALUES('" + currentVersionString + "');");
            }
            else {
                log.info("Update latest version to {} in database", currentVersionString);
                statement.executeUpdate("UPDATE artemis_version SET latest_version = '" + currentVersionString + "';");
            }
            statement.getConnection().commit();
            statement.closeOnCompletion();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
