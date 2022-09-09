package de.tum.in.www1.artemis.config;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    public LiquibaseConfiguration(Environment env, BuildProperties buildProperties) {
        this.env = env;
        this.buildProperties = buildProperties;
    }

    /**
     * reads properties and configures liquibase
     *
     * @param liquibaseDataSource the liquibase sql data source
     * @param liquibaseProperties the liquibase properties
     * @param dataSource the sql data source
     * @param dataSourceProperties data source properties
     * @return the configured spring liquibase object
     */
    @Bean
    public SpringLiquibase liquibase(@LiquibaseDataSource ObjectProvider<DataSource> liquibaseDataSource, LiquibaseProperties liquibaseProperties,
            ObjectProvider<DataSource> dataSource, DataSourceProperties dataSourceProperties) {

        checkMigrationPath(dataSource.getIfUnique());

        SpringLiquibase liquibase = SpringLiquibaseUtil.createSpringLiquibase(liquibaseDataSource.getIfAvailable(), liquibaseProperties, dataSource.getIfUnique(),
                dataSourceProperties);
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

    String initialCheckSum5_10_3_String = "8:ebe0513b909c8b3f341dfa8a1278600e";

    private void checkMigrationPath(DataSource dataSource) {

        var currentVersion = new Semver(buildProperties.getVersion());

        var migrationPathVersion = new Semver(migrationPathVersion5_10_3_String);
        var version600 = new Semver("6.0.0");
        var version700 = new Semver("7.0.0");
        if (currentVersion.isLowerThan(version600)) {
            log.info("Migration path check: Not necessary");
        }
        if (currentVersion.isGreaterThanOrEqualTo(version600) && currentVersion.isLowerThan(version700)) {
            var previousVersionString = getPreviousVersionElseThrow(dataSource);
            if (previousVersionString == null) {
                // this means Artemis was never started before and no DATABASECHANGELOG exists, we can simply proceed
                return;
            }
            var previousVersion = new Semver(previousVersionString);
            if (previousVersion.isLowerThan(migrationPathVersion)) {
                log.error("Cannot start Artemis. Please start the release {} first, otherwise the migration will fail", migrationPathVersion5_10_3_String);
            }
            else if (previousVersion.isEqualTo(migrationPathVersion)) {
                updateInitialChecksum(dataSource, initialCheckSum5_10_3_String);
            }
        }

    }

    private String getPreviousVersionElseThrow(DataSource dataSource) {
        Statement statement;
        try {
            var connection = dataSource.getConnection();
            statement = connection.createStatement();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }

        String error = "Cannot start Artemis because version table does not exist, but a migration path is necessary! Please start the release " + migrationPathVersion5_10_3_String
                + " first, otherwise the migration will fail";
        ResultSet result;
        try {
            result = statement.executeQuery("SELECT * FROM DATABASECHANGELOG");
            result = statement.executeQuery("SELECT latest_version FROM artemis_version");
            if (result.next()) {
                return result.getString("latest_version");
            }
            // if no version exists, we fail here
            log.error(error);
            throw new RuntimeException(error);
        }
        catch (SQLException e) {
            // TODO: generalize this
            if (e.getMessage().contains("Table 'artemis_new.databasechangelog' doesn't exist") || e.getMessage().contains("ERROR: relation \"databasechangelog\" does not exist")) {
                return null;
            }
            log.error(error);
            throw new RuntimeException(error, e);
        }
    }

    private void updateInitialChecksum(DataSource dataSource, String newCheckSum) {
        Statement statement;
        try {
            var connection = dataSource.getConnection();
            statement = connection.createStatement();
            statement.execute("""
                        UPDATE
                            `DATABASECHANGELOG`
                        SET
                            `DATEEXECUTED` = now(),
                            `MD5SUM` = '%s',
                            `DESCRIPTION` = 'Initial schema',
                            `LIQUIBASE` = '4.15.0',
                            `DEPLOYMENT_ID` = NULL,
                            `FILENAME` = 'config/liquibase/changelog/00000000000000_initial_schema.xml'
                        WHERE
                            `ID` = '00000000000001'
                        LIMIT 1;
                    """.formatted(newCheckSum));
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }
}
