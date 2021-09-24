package de.tum.in.www1.artemis.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import liquibase.integration.spring.SpringLiquibase;
import tech.jhipster.config.JHipsterConstants;
import tech.jhipster.config.liquibase.SpringLiquibaseUtil;

@Configuration
public class LiquibaseConfiguration {

    private final Logger log = LoggerFactory.getLogger(LiquibaseConfiguration.class);

    private final Environment env;

    public LiquibaseConfiguration(Environment env) {
        this.env = env;
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
}
