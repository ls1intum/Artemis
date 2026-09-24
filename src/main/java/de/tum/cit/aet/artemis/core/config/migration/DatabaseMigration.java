package de.tum.cit.aet.artemis.core.config.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.sql.DataSource;

import org.apache.commons.lang3.Strings;
import org.semver4j.Semver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.util.ArtemisVersionUtil;
import de.tum.cit.aet.helios.HeliosClient;

/**
 * One version boundary an upgrade has to pass through, expressed as the release an administrator must
 * have deployed before a given major version will start.
 * <p>
 * A path exists where the chain of changelogs leading to the current schema is no longer complete for
 * databases older than {@link #requiredVersion}, so those cannot be migrated forward at all. Starting
 * from an empty database is always an alternative and never needs a path.
 * <p>
 * See {@link DatabaseMigration} for what breaks a chain, and the Upgrade Guide under
 * {@code documentation/docs/admin/upgrade-guide.mdx} for the operator-facing table of these paths.
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
        this.requiredVersion = ArtemisVersionUtil.parseForComparison(requiredVersion);
        this.upgradeVersion = this.requiredVersion.nextMajor();
        this.nextUpgradeVersion = upgradeVersion.nextMajor();
        this.errorMessage = "Cannot start Artemis because the migration path was not followed. Please deploy and start the release " + requiredVersion
                + " first, otherwise the migration will fail";
    }
}

/**
 * Refuses to start when the database is too old for this release to migrate it.
 * <p>
 * Every upgrade path Artemis supports is a chain of changelogs that ends at the current schema. A
 * database that has not run some link in that chain cannot be brought forward, and the only safe
 * response is to stop before Liquibase touches it and to name the release the operator has to deploy
 * first.
 * <p>
 * Two things can break the chain, and only the first of them still can:
 * <ul>
 * <li><b>Reused changeset identities.</b> Before the 10.0 baseline, each consolidation rewrote the
 * contents of {@code 00000000000000_initial_schema.xml} while keeping its changeset ids, so a database
 * that had recorded the previous major's initial schema saw a checksum that no longer matched. The
 * consolidation papered over that by nulling the checksum on the way past, which only worked from the
 * one version it was written for. Every baseline from 10.0 on gets its own file and its own ids
 * ({@code v10-01}, {@code v11-01}, ...), so nothing collides and nothing has to be papered over.</li>
 * <li><b>Deleting a folded generation.</b> Removing a directory under {@code config/liquibase/history}
 * drops the changelogs it holds, so a database that had not yet run them can no longer be brought
 * forward. That is a deliberate retention decision and the only reason a new path is needed from now
 * on; the required version is the last release of the oldest generation still present.</li>
 * </ul>
 * <p>
 * The layout and the reasoning behind it are documented in
 * {@code documentation/docs/developer/guidelines/database-migration-consolidation.mdx}.
 */
public class DatabaseMigration {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigration.class);

    private final DataSource dataSource;

    private final List<MigrationPath> migrationPaths = new ArrayList<>();

    private final String currentVersionString;

    private String previousVersionString;

    private final Optional<HeliosClient> optionalHeliosClient;

    public DatabaseMigration(String currentVersionString, DataSource dataSource, Optional<HeliosClient> optionalHeliosClient) {
        this.currentVersionString = currentVersionString;
        this.dataSource = dataSource;
        this.optionalHeliosClient = optionalHeliosClient;

        // Initialize migration paths here in the correct order
        migrationPaths.add(new MigrationPath("5.12.9")); // required for migration to 6.0.0 until 7.0.0
        migrationPaths.add(new MigrationPath("6.9.6"));  // required for migration to 7.0.0 until 8.0.0
        migrationPaths.add(new MigrationPath("7.10.5"));  // required for migration to 8.0.0 until 9.0.0
        migrationPaths.add(new MigrationPath("8.8.6"));  // required for migration to 9.0.0 until 10.0.0
        // Required for migration to 10.0.0 until 11.0.0. This is the last path the reused-changeset-id
        // problem forces: a 9.x database recorded the 9.0 initial schema under the ids 00000000000001 to
        // 00000000000003, and an 8.x database recorded the 8.0 one under the same ids, so only a database
        // that has been through 9.x carries checksums that still match the file. From the v10 baseline on
        // each generation owns its ids, and a new path is needed only when a history generation is deleted.
        migrationPaths.add(new MigrationPath("9.9.3"));
    }

    public String getPreviousVersionString() {
        return previousVersionString;
    }

    /**
     * Stops the startup when the recorded version is older than this release can migrate from.
     * <p>
     * A database with no recorded version is a fresh installation, which needs no path: the baseline
     * creates the schema outright.
     */
    public void checkMigrationPath() {
        var currentVersion = ArtemisVersionUtil.parseForComparison(currentVersionString);
        previousVersionString = getPreviousVersionElseThrow();

        if (previousVersionString == null) {
            log.info("Migration path check: Not necessary");
            optionalHeliosClient.ifPresent(HeliosClient::pushDbMigrationStarted);
            return;
        }

        var previousVersion = ArtemisVersionUtil.parseForComparison(previousVersionString);

        for (MigrationPath path : migrationPaths) {
            boolean pathApplies = currentVersion.isGreaterThanOrEqualTo(path.upgradeVersion) && currentVersion.isLowerThan(path.nextUpgradeVersion);
            if (pathApplies && previousVersion.isLowerThan(path.requiredVersion)) {
                log.error(path.errorMessage);
                optionalHeliosClient.ifPresent(HeliosClient::pushDbMigrationFailed);
                System.exit(15);
            }
        }

        optionalHeliosClient.ifPresent(HeliosClient::pushDbMigrationStarted);
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
        try (var connection = openConnection(); var statement = connection.createStatement()) {
            statement.executeQuery("SELECT * FROM DATABASECHANGELOG;");
            var result = statement.executeQuery("SELECT latest_version FROM artemis_version;");
            if (result.next()) {
                return result.getString("latest_version");
            }
            // if no version is recorded in the table, we proceed with the startup
            return null;
        }
        catch (SQLException e) {
            // Defaulted, because a driver may raise an SQLException with no message and this branch is now reachable for
            // a failure to connect: opening the connection throws here, where the previous helper exited the JVM first.
            String message = Objects.requireNonNullElse(e.getMessage(), "");
            boolean isEmptyH2Database = message.contains("not found");
            if (Strings.CI.contains(message, "databasechangelog") && (message.contains("does not exist") || message.contains("doesn't exist") || isEmptyH2Database)) {
                return null;
            }
            log.error(error, e);
            System.exit(13);
        }
        // this path cannot happen
        return null;
    }

    /**
     * Opens a connection to the application database for the migration checks in this class.
     * <p>
     * Callers own the returned connection and must close it, which also closes any statement derived from it.
     * Returning the connection rather than a ready-made statement is deliberate: closing a statement does not close
     * the connection behind it, so a helper that handed out statements leaked one connection per call.
     * <p>
     * A failure to connect is terminal for startup rather than something a caller can recover from: the schema is
     * unverified, so the node must not proceed to serve requests against it.
     *
     * @return A new {@link Connection} to the application database.
     * @throws SQLException If opening the connection fails due to database access errors.
     */
    private Connection openConnection() throws SQLException {
        try {
            return dataSource.getConnection();
        }
        catch (Exception e) {
            log.error("Cannot connect to the database {} (This typically indicates that the database is not running or there are permission issues", e.getMessage());
            optionalHeliosClient.ifPresent(HeliosClient::pushDbMigrationFailed);
            System.exit(10);
        }
        return null;
    }
}
