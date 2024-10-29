package de.tum.cit.aet.artemis.core.config.migration;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vdurmont.semver4j.Semver;

/**
 * Represents a migration path that defines the necessary steps for database migration before
 * updating to a new major version. This class is pivotal in ensuring that administrators install
 * the last minor version before a major update to guarantee that all database changes are
 * incorporated. Alternatively, administrators have the option to start from scratch.
 * <p>
 * The rationale behind this approach is to merge all Liquibase migrations and remove Java migrations
 * with every major version release. This strategy is aimed at accelerating the application startup,
 * particularly enhancing the performance for server and e2e tests.
 * <p>
 * This class simplifies the addition of future migrations by abstracting the migration logic into
 * a structured form.
 */
class MigrationPath {

    /** The version from which migration can start, typically the last version before a major update. */
    final Semver requiredVersion; // e.g. 5.12.9

    /** The upgrade version to which the database can be migrated. */
    final Semver upgradeVersion; // e.g. 6.0.0 --> this is also the target version

    /** The next upgrade version before another major update is needed. */
    final Semver nextUpgradeVersion; // e.g. 7.0.0

    /** The error message to be displayed if the migration cannot proceed due to version incompatibility. */
    final String errorMessage;

    /**
     * Constructs a MigrationPath instance by defining the required version for the migration,
     * and automatically determining the earliest and latest new versions based on major version
     * increments.
     *
     * @param requiredVersion The minimum version required to start the migration, in string format.
     *                            The earliestNewVersion is derived as the next major version after
     *                            this required version, and latestNewVersion is the next major version
     *                            after earliestNewVersion.
     */
    public MigrationPath(String requiredVersion) {
        this.upgradeVersion = new Semver(requiredVersion).nextMajor();
        this.requiredVersion = new Semver(requiredVersion);
        this.nextUpgradeVersion = upgradeVersion.nextMajor();
        this.errorMessage = "Cannot start Artemis because the migration path was not followed. Please deploy and start the release " + requiredVersion
                + " first, otherwise the migration will fail";
    }
}

/**
 * Handles the database migration process by checking against defined migration paths to ensure
 * that the database schema is up-to-date before application startup. This class plays a critical
 * role in optimizing application startup time by merging Liquibase migrations and eliminating Java
 * migrations for each major version release.
 * <p>
 * This mechanism is essential for maintainers, aiming to streamline the startup, especially
 * beneficial for server and e2e tests. It mandates administrators to upgrade to the latest minor
 * version before a major update, facilitating a smooth transition and incorporation of all
 * database changes.
 * <p>
 * Steps for new database migration paths:
 * <ol>
 * <li>Re-create the new initial scheme from the database after applying all existing liquibase migrations.</li>
 * <li>Delete all existing liquibase migrations except the new initial scheme and *_cleanup.xml (adapt *_cleanup.xml with the latest date).</li>
 * <li>Delete all Java migrations that have been executed before.</li>
 * <li>Check that the new initial scheme is compatible with MySQL and Postgres and can be started from scratch in both environments.</li>
 * <li>Add the new migration path in the constructor of DatabaseMigration.</li>
 * <li>Verify that the migration works with the designated path (ideally with a local database and with a dump from a test / production system).</li>
 * <li>Document the migration carefully in the release notes and the online documentation.</li>
 * </ol>
 */
public class DatabaseMigration {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigration.class);

    private final DataSource dataSource;

    private final List<MigrationPath> migrationPaths = new ArrayList<>();

    private final String currentVersionString;

    private String previousVersionString;

    public DatabaseMigration(String currentVersionString, DataSource dataSource) {
        this.currentVersionString = currentVersionString;
        this.dataSource = dataSource;

        // Initialize migration paths here in the correct order
        migrationPaths.add(new MigrationPath("5.12.9")); // required for migration to 6.0.0 until 7.0.0
        migrationPaths.add(new MigrationPath("6.9.6"));  // required for migration to 7.0.0 until 8.0.0

        // Add more migrations here as needed
    }

    public String getPreviousVersionString() {
        return previousVersionString;
    }

    /**
     * Checks against the defined migration paths to determine if the current version of the database
     * is compatible or requires migration. This method ensures that the database schema is prepared
     * and up-to-date before proceeding with the application startup.
     */
    public void checkMigrationPath() {
        var currentVersion = new Semver(currentVersionString);
        previousVersionString = getPreviousVersionElseThrow();

        if (previousVersionString == null) {
            log.info("Migration path check: Not necessary");
            return;
        }

        var previousVersion = new Semver(previousVersionString);

        for (MigrationPath path : migrationPaths) {
            if (currentVersion.isGreaterThanOrEqualTo(path.upgradeVersion) && currentVersion.isLowerThan(path.nextUpgradeVersion)) {
                if (previousVersion.isLowerThan(path.requiredVersion)) {
                    log.error(path.errorMessage);
                    System.exit(15);
                }
                else if (previousVersion.isEqualTo(path.requiredVersion)) {
                    updateInitialChecksum(path.upgradeVersion.toString());
                    log.info("Successfully cleaned up initial schema during migration");
                    break; // Exit after handling the required migration step
                }
            }
        }
    }

    /**
     * Attempts to retrieve the latest version of the application from the 'artemis_version' table in the database.
     * This method is crucial for determining whether a database migration is necessary by comparing the current
     * application version with the version stored in the database.
     * <p>
     * The method performs the following operations:
     * <ol>
     * <li>Attempts to query the 'DATABASECHANGELOG' table to ensure the database is initialized.</li>
     * <li>Queries the 'artemis_version' table for the latest version recorded.</li>
     * <li>Returns the latest version if it exists.</li>
     * </ol>
     * If the 'DATABASECHANGELOG' table does not exist, implying that the database is not yet initialized, the method
     * returns {@code null}, indicating that a full migration or initialization is required.
     * <p>
     * If the 'artemis_version' table does not exist, this method throws a {@link RuntimeException},
     * signaling a critical migration issue that must be resolved by installing a specific version of the application
     * (as mentioned in the thrown error message) before proceeding.
     * <p>
     * This method ensures that the application's database schema is compatible with the application's current version,
     * adhering to the migration path requirements.
     *
     * @return The latest version string from the 'artemis_version' table if it exists.
     * @throws RuntimeException If the 'artemis_version' table does not exist
     */
    private String getPreviousVersionElseThrow() {
        String error = "Cannot start Artemis because version table does not exist, but a migration path is necessary! Please start the release 5.12.9 first, otherwise the migration will fail";
        try (var statement = createStatement()) {
            statement.executeQuery("SELECT * FROM DATABASECHANGELOG;");
            var result = statement.executeQuery("SELECT latest_version FROM artemis_version;");
            statement.closeOnCompletion();
            if (result.next()) {
                return result.getString("latest_version");
            }
            // if no version is recorded in the table, we proceed with the startup
            return null;
        }
        catch (SQLException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "databasechangelog") && (e.getMessage().contains("does not exist") || (e.getMessage().contains("doesn't exist")))) {
                return null;
            }
            log.error(error, e);
            System.exit(13);
        }
        // this path cannot happen
        return null;
    }

    /**
     * Updates the checksum of the initial schema in the 'DATABASECHANGELOG' table to {@code null}.
     * This operation is crucial for allowing Liquibase to recalculate the checksum during the
     * migration process, ensuring that the database schema is up-to-date with the specified new version
     * of the application. This method specifically targets the initial schema entry, preparing it for
     * a fresh checksum calculation by Liquibase.
     * <p>
     * The update is performed with the intention of aligning the database schema with the new version,
     * thereby facilitating a smooth transition and avoiding potential migration conflicts.
     * <p>
     * If an SQLException occurs during the update process, a RuntimeException is thrown, indicating
     * that the checksum update operation has failed. This failure needs to be addressed to ensure
     * the integrity and consistency of the database schema migration process.
     *
     * @param newVersion The new version of the application for which the initial schema checksum needs to be updated.
     * @throws RuntimeException If updating the checksum fails due to an SQLException, encapsulating the original exception.
     */
    private void updateInitialChecksum(String newVersion) {
        String description = "Initial schema generation for version " + newVersion;

        // SQL statement with a placeholder for the newVersion parameter
        String updateSqlStatement = """
                UPDATE DATABASECHANGELOG
                SET MD5SUM = null,
                    DATEEXECUTED = now(),
                    DESCRIPTION = ?,
                    LIQUIBASE = '4.27.0',
                    FILENAME = 'config/liquibase/changelog/00000000000000_initial_schema.xml'
                WHERE ID = '00000000000001';
                """;

        // Use try-with-resources to ensure resources are closed properly
        try (var connection = dataSource.getConnection(); var preparedStatement = connection.prepareStatement(updateSqlStatement)) {

            // Set the newVersion parameter in the SQL statement
            preparedStatement.setString(1, description);

            // Execute the update
            preparedStatement.executeUpdate();

            // Commit the transaction
            connection.commit();

            log.info("Set checksum of initial schema to null so that liquibase will recalculate it");
        }
        catch (SQLException e) {
            log.error("Cannot update checksum for initial schema migration: {}", e.getMessage());
            System.exit(11);
        }
    }

    /**
     * Creates and returns a new SQL {@link Statement} object for executing queries against the database.
     * This utility method facilitates the creation of a Statement object from the current database
     * connection, simplifying the execution of SQL commands within the application.
     * <p>
     * The method leverages the established dataSource connection to instantiate a new Statement,
     * providing a means to execute SQL queries and updates. It is a fundamental operation used
     * across various database interaction methods within the application, ensuring consistent
     * and efficient database access.
     * <p>
     * Should an SQLException occur while attempting to create the Statement, the exception is
     * propagated upwards, necessitating handling by the caller to manage potential database
     * access issues or failures.
     *
     * @return A new {@link Statement} object for database interaction.
     * @throws SQLException If creating the Statement object fails due to database access errors.
     */
    private Statement createStatement() throws SQLException {
        try {
            var connection = dataSource.getConnection();
            return connection.createStatement();
        }
        catch (Exception e) {
            log.error("Cannot connect to the database {} (This typically indicates that the database is not running or there are permission issues", e.getMessage());
            System.exit(10);
        }
        return null;
    }
}
