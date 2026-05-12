package de.tum.cit.aet.artemis.core.config.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link DatabaseMigration}.
 * <p>
 * Verifies that the migration logic accepts two-part canonical versions (e.g. {@code "9.2"})
 * and three-part hotfix versions (e.g. {@code "9.2.1"}), and that the user-facing current
 * version (not the internally padded semver) lands in the {@code DATABASECHANGELOG} description.
 */
class DatabaseMigrationTest {

    private DataSource dataSource;

    private Connection connection;

    private Statement statement;

    private PreparedStatement preparedStatement;

    private ResultSet versionResultSet;

    private ResultSet consolidationResultSet;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(Statement.class);
        preparedStatement = mock(PreparedStatement.class);
        versionResultSet = mock(ResultSet.class);
        consolidationResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        // SELECT * FROM DATABASECHANGELOG returns an empty result set (just needs to succeed).
        ResultSet changelogProbe = mock(ResultSet.class);
        when(statement.executeQuery("SELECT * FROM DATABASECHANGELOG;")).thenReturn(changelogProbe);
        when(statement.executeQuery("SELECT latest_version FROM artemis_version;")).thenReturn(versionResultSet);
        lenient().when(statement.executeQuery("SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID = '20260406120000';")).thenReturn(consolidationResultSet);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
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
        // Consolidation already completed so no checksum work either.
        when(consolidationResultSet.next()).thenReturn(true);
        when(consolidationResultSet.getInt(1)).thenReturn(1);

        DatabaseMigration migration = new DatabaseMigration("9.2", dataSource, Optional.empty());
        migration.checkMigrationPath();

        assertThat(migration.getPreviousVersionString()).isEqualTo("9.1.3");
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    void updatesChecksumWithUserFacingTwoPartVersionWhenUpgradingMajor() throws Exception {
        when(versionResultSet.next()).thenReturn(true);
        when(versionResultSet.getString("latest_version")).thenReturn("8.8.6");
        // Consolidation not yet completed → updateInitialChecksum should run.
        when(consolidationResultSet.next()).thenReturn(true);
        when(consolidationResultSet.getInt(1)).thenReturn(0);

        DatabaseMigration migration = new DatabaseMigration("9.2", dataSource, Optional.empty());
        migration.checkMigrationPath();

        ArgumentCaptor<String> descriptionCaptor = ArgumentCaptor.forClass(String.class);
        verify(preparedStatement, atLeastOnce()).setString(eq(1), descriptionCaptor.capture());
        // The DESCRIPTION must contain the user-facing "9.2", not the internal "9.0.0" or "9.2.0".
        assertThat(descriptionCaptor.getValue()).isEqualTo("Initial schema generation for version 9.2");
        verify(preparedStatement, times(1)).executeUpdate();
        verify(connection, times(1)).commit();
    }

    @Test
    void hotfixUpgradeIsNoOpWhenConsolidationAlreadyCompleted() throws Exception {
        when(versionResultSet.next()).thenReturn(true);
        when(versionResultSet.getString("latest_version")).thenReturn("9.2");
        when(consolidationResultSet.next()).thenReturn(true);
        when(consolidationResultSet.getInt(1)).thenReturn(1);

        DatabaseMigration migration = new DatabaseMigration("9.2.1", dataSource, Optional.empty());
        migration.checkMigrationPath();

        assertThat(migration.getPreviousVersionString()).isEqualTo("9.2");
        verify(connection, never()).prepareStatement(anyString());
    }

}
