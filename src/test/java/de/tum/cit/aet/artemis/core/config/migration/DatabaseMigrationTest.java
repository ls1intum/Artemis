package de.tum.cit.aet.artemis.core.config.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DatabaseMigration}.
 * <p>
 * The class has one job: refuse to start when the recorded version is older than this release can
 * migrate from. These tests cover the version arithmetic that decides that, including the two-part
 * canonical versions ({@code "9.2"}) and three-part hotfix versions ({@code "9.2.1"}) Artemis uses.
 * <p>
 * A migration that is allowed to proceed must not write to the database here. Liquibase does the
 * migrating; this class only decides whether it may start, which is why every accepting case asserts
 * that no statement was prepared.
 */
class DatabaseMigrationTest {

    private DataSource dataSource;

    private Connection connection;

    private ResultSet versionResultSet;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        versionResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        // SELECT * FROM DATABASECHANGELOG returns an empty result set (just needs to succeed).
        when(statement.executeQuery("SELECT * FROM DATABASECHANGELOG;")).thenReturn(mock(ResultSet.class));
        when(statement.executeQuery("SELECT latest_version FROM artemis_version;")).thenReturn(versionResultSet);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(mock(java.sql.PreparedStatement.class));
    }

    @Test
    void doesNothingWhenNoPreviousVersionRecorded() throws Exception {
        when(versionResultSet.next()).thenReturn(false);

        DatabaseMigration migration = new DatabaseMigration("9.2", dataSource, Optional.empty());
        migration.checkMigrationPath();

        assertThat(migration.getPreviousVersionString()).isNull();
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    void doesNothingWhenPreviousAndCurrentAreInSameMajorWindow() throws Exception {
        when(versionResultSet.next()).thenReturn(true);
        when(versionResultSet.getString("latest_version")).thenReturn("9.1.3");

        DatabaseMigration migration = new DatabaseMigration("9.2", dataSource, Optional.empty());
        migration.checkMigrationPath();

        assertThat(migration.getPreviousVersionString()).isEqualTo("9.1.3");
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    void acceptsAnUpgradeThatFollowedTheRequiredPath() throws Exception {
        when(versionResultSet.next()).thenReturn(true);
        when(versionResultSet.getString("latest_version")).thenReturn("8.8.6");

        DatabaseMigration migration = new DatabaseMigration("9.2", dataSource, Optional.empty());
        migration.checkMigrationPath();

        assertThat(migration.getPreviousVersionString()).isEqualTo("8.8.6");
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    void acceptsAHotfixUpgradeWithinTheSameMajor() throws Exception {
        when(versionResultSet.next()).thenReturn(true);
        when(versionResultSet.getString("latest_version")).thenReturn("9.2");

        DatabaseMigration migration = new DatabaseMigration("9.2.1", dataSource, Optional.empty());
        migration.checkMigrationPath();

        assertThat(migration.getPreviousVersionString()).isEqualTo("9.2");
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    void acceptsAnUpgradeToTenFromTheRequiredNineRelease() throws Exception {
        when(versionResultSet.next()).thenReturn(true);
        when(versionResultSet.getString("latest_version")).thenReturn("9.9.3");

        DatabaseMigration migration = new DatabaseMigration("10.0", dataSource, Optional.empty());
        migration.checkMigrationPath();

        assertThat(migration.getPreviousVersionString()).isEqualTo("9.9.3");
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    void migrationPathAcceptsTwoPartRequiredVersion() {
        MigrationPath path = new MigrationPath("9.2");

        assertThat(path.requiredVersion.getMajor()).isEqualTo(9);
        assertThat(path.requiredVersion.getMinor()).isEqualTo(2);
        assertThat(path.upgradeVersion.getMajor()).isEqualTo(10);
        assertThat(path.upgradeVersion.getMinor()).isEqualTo(0);
        assertThat(path.nextUpgradeVersion.getMajor()).isEqualTo(11);
        assertThat(path.nextUpgradeVersion.getMinor()).isEqualTo(0);
    }

}
