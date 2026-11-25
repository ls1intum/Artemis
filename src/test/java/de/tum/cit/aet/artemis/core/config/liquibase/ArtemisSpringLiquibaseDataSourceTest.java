package de.tum.cit.aet.artemis.core.config.liquibase;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import liquibase.integration.spring.SpringLiquibase;

/**
 * Checks which DataSource {@link ArtemisSpringLiquibase#createSpringLiquibase} hands to Liquibase.
 * <p>
 * A Liquibase connection of its own is how the migrations get settings that differ from those of the
 * application sessions, such as a longer {@code statement_timeout}, through the JDBC URL.
 */
class ArtemisSpringLiquibaseDataSourceTest {

    private static final String SEPARATE_URL = "jdbc:postgresql://db.example:5432/Artemis?options=-c%20statement_timeout=30min";

    private final DataSource applicationDataSource = new DriverManagerDataSource("jdbc:postgresql://db.example:5432/Artemis", "application", "application-secret");

    private LiquibaseProperties liquibaseProperties;

    private DataSourceProperties dataSourceProperties;

    @BeforeEach
    void setUp() {
        liquibaseProperties = new LiquibaseProperties();
        dataSourceProperties = new DataSourceProperties();
        dataSourceProperties.setUsername("application");
        dataSourceProperties.setPassword("application-secret");
    }

    @Test
    void usesTheApplicationDataSourceWhenNothingIsConfiguredForLiquibase() {
        SpringLiquibase liquibase = ArtemisSpringLiquibase.createSpringLiquibase(null, liquibaseProperties, applicationDataSource, dataSourceProperties);

        assertThat(liquibase.getDataSource()).isSameAs(applicationDataSource);
    }

    @Test
    void opensASeparateConnectionFromTheLiquibaseUrlAndTakesUserAndPasswordFromTheApplication() {
        liquibaseProperties.setUrl(SEPARATE_URL);

        DriverManagerDataSource separate = separateDataSource();

        assertThat(separate.getUrl()).as("the options of the URL are what carry the settings of the migration connection").isEqualTo(SEPARATE_URL);
        assertThat(separate.getUsername()).isEqualTo("application");
        assertThat(separate.getPassword()).isEqualTo("application-secret");
    }

    @Test
    void aUserAndPasswordOfLiquibaseOverrideThoseOfTheApplication() {
        liquibaseProperties.setUrl(SEPARATE_URL);
        liquibaseProperties.setUser("migration");
        liquibaseProperties.setPassword("migration-secret");

        DriverManagerDataSource separate = separateDataSource();

        assertThat(separate.getUsername()).isEqualTo("migration");
        assertThat(separate.getPassword()).isEqualTo("migration-secret");
    }

    @Test
    void theSeparateConnectionDoesNotPool() {
        liquibaseProperties.setUrl(SEPARATE_URL);

        SpringLiquibase liquibase = ArtemisSpringLiquibase.createSpringLiquibase(null, liquibaseProperties, applicationDataSource, dataSourceProperties);

        // A pool would keep its idle connections open for as long as the application runs. Nothing closes the
        // DataSource after the migrations, so it must be one whose connections are really closed by Liquibase.
        assertThat(liquibase.getDataSource()).as("a separate Liquibase connection must not leave a pool behind").isExactlyInstanceOf(DriverManagerDataSource.class);
    }

    @Test
    void aDedicatedLiquibaseDataSourceWinsOverEverythingElse() {
        liquibaseProperties.setUrl(SEPARATE_URL);
        DataSource dedicated = new DriverManagerDataSource("jdbc:postgresql://db.example:5432/Artemis", "dedicated", "dedicated-secret");

        SpringLiquibase liquibase = ArtemisSpringLiquibase.createSpringLiquibase(dedicated, liquibaseProperties, applicationDataSource, dataSourceProperties);

        assertThat(liquibase.getDataSource()).isSameAs(dedicated);
    }

    private DriverManagerDataSource separateDataSource() {
        SpringLiquibase liquibase = ArtemisSpringLiquibase.createSpringLiquibase(null, liquibaseProperties, applicationDataSource, dataSourceProperties);
        assertThat(liquibase.getDataSource()).isInstanceOf(DriverManagerDataSource.class).isNotSameAs(applicationDataSource);
        return (DriverManagerDataSource) liquibase.getDataSource();
    }
}
