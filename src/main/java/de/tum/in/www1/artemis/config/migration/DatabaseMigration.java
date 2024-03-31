package de.tum.in.www1.artemis.config.migration;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vdurmont.semver4j.Semver;

class MigrationPath {

    Semver requiredVersion; // e.g. 5.12.9

    Semver earliestNewVersion; // e.g. 6.0.0 --> this is also the target version

    Semver latestNewVersion; // e.g. 7.0.0

    String errorMessage;

    public MigrationPath(String requiredVersion) {
        this.earliestNewVersion = new Semver(requiredVersion).nextMajor();
        this.requiredVersion = new Semver(requiredVersion);
        this.latestNewVersion = earliestNewVersion.nextMajor();
        this.errorMessage = "Cannot start Artemis. Please start the release " + requiredVersion + " first, otherwise the migration will fail";
    }
}

public class DatabaseMigration {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigration.class);

    private final DataSource dataSource;

    private final List<MigrationPath> migrationPaths = new ArrayList<>();

    private final String currentVersionString;

    private String previousVersionString;

    public DatabaseMigration(String currentVersionString, DataSource dataSource) {
        this.currentVersionString = currentVersionString;
        this.dataSource = dataSource;

        // Initialize your migration paths here in the correct order
        migrationPaths.add(new MigrationPath("5.12.9")); // needed between 6.0.0 and 7.0.0
        migrationPaths.add(new MigrationPath("6.9.6")); // needed between 7.0.0 and 8.0.0
        // Add more migrations here as needed
    }

    /*
     * Steps for new database migration paths:
     * 1. Re-create the new initial scheme from the database after applying all existing liquibase migrations
     * 2. Delete all existing liquibase migrations except the new initial scheme and *_cleanup.xml (adapt *_cleanup.xml with the latest date)
     * 3. Delete all Java migrations that have been executed before
     * 4. Check that the new initial scheme is compatible with MySQL and Postgres and can be started from scratch in both environments
     * 5. Add the new migration path above
     * 6. Verify that the migration works with the designated path (ideally with a local database and with a dump from a test / production system)
     * 7. Document the migration carefully in the release notes and the online documentation
     */

    public String getPreviousVersionString() {
        return previousVersionString;
    }

    public void checkMigrationPath() {
        var currentVersion = new Semver(currentVersionString);
        previousVersionString = getPreviousVersionElseThrow();

        if (previousVersionString == null) {
            log.info("Migration path check: Not necessary");
            return;
        }

        var previousVersion = new Semver(previousVersionString);

        for (MigrationPath path : migrationPaths) {
            if (currentVersion.isGreaterThanOrEqualTo(path.earliestNewVersion) && currentVersion.isLowerThan(path.latestNewVersion)) {
                if (previousVersion.isLowerThan(path.requiredVersion)) {
                    log.error(path.errorMessage);
                    throw new RuntimeException(path.errorMessage);
                }
            }
            else if (previousVersion.isEqualTo(path.requiredVersion)) {
                updateInitialChecksum(path.earliestNewVersion.toString());
                log.info("Successfully cleaned up initial schema during migration");
                break; // Exit after handling the required migration step
            }
        }
    }

    private String getPreviousVersionElseThrow() {
        String error = "Cannot start Artemis because version table does not exist, but a migration path is necessary! Please start the release 5.12.9 first, otherwise the migration will fail";
        try (var statement = createStatement()) {
            statement.executeQuery("SELECT * FROM DATABASECHANGELOG;");
            var result = statement.executeQuery("SELECT latest_version FROM artemis_version;");
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

    private void updateInitialChecksum(String newVersion) {
        try (var statement = createStatement()) {
            log.info("Set checksum of initial schema to null so that liquibase will recalculate it");
            statement.executeUpdate("UPDATE DATABASECHANGELOG SET MD5SUM = null, DATEEXECUTED = now(), DESCRIPTION = 'Initial schema generation for version '" + newVersion
                    + "', LIQUIBASE = '4.27.0', FILENAME = 'config/liquibase/changelog/00000000000000_initial_schema.xml' WHERE ID = '00000000000001';");
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
}
