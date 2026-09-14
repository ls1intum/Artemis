package de.tum.cit.aet.artemis.core.config.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * Verifies the MySQL-only migration {@code 20260825000000_changelog.xml}, which converts the date columns of
 * {@code user_public_ssh_key} from TIMESTAMP to DATETIME(3) to remove the Year-2038 ceiling of MySQL TIMESTAMP.
 * <p>
 * The migration is not observable in the regular test suite because that runs against PostgreSQL, whose
 * {@code timestamp} type has no such ceiling. This test therefore drives Liquibase against a real MySQL container.
 * <p>
 * The central guarantee is that the migration must not change the value Artemis sees for an existing SSH key. Artemis
 * reads these columns through Hibernate with {@code hibernate.jdbc.time_zone=UTC}, i.e. as {@code getTimestamp} with a
 * UTC {@link Calendar}. This test writes and reads through that exact boundary under a non-UTC session, so a migration
 * that shifted the application-visible value (for example by converting under a forced UTC session) would fail here.
 */
@EnabledIf("isDockerAvailable")
class SshKeyDateColumnMigrationMySqlTest {

    private static final String CHANGELOG = "config/liquibase/changelog/20260825000000_changelog.xml";

    private static final String NON_UTC_SESSION_ZONE = "+02:00";

    // Millisecond-aligned instants, written the way Artemis writes them, to avoid assumptions about fractional rounding.
    private static final Instant CREATION_INSTANT = Instant.parse("2030-06-15T12:00:00Z");

    private static final Instant LAST_USED_INSTANT = Instant.parse("2031-01-02T08:30:00Z");

    private static final Instant EXPIRY_INSTANT = Instant.parse("2032-03-04T03:00:00Z");

    private static MySQLContainer mysql;

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        }
        catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    static void beforeAll() {
        String mysqlVersion = System.getProperty("mysql.version", "9.7.2");
        mysql = new MySQLContainer(DockerImageName.parse("mysql:" + mysqlVersion));
        mysql.start();
    }

    @AfterAll
    static void afterAll() {
        if (mysql != null) {
            mysql.stop();
        }
    }

    @Test
    void shouldConvertSshKeyDateColumnsToDatetimePreservingApplicationVisibleValues() throws Exception {
        Instant[] valuesBeforeMigration;

        // Seed the pre-migration schema under a non-UTC session, mirroring an existing MySQL instance.
        try (Connection connection = newConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET time_zone = '+02:00'");
            assertSessionZoneIsNonUtc(statement);

            statement.execute("""
                    CREATE TABLE user_public_ssh_key (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        label VARCHAR(50) NOT NULL,
                        public_key VARCHAR(1000) NOT NULL,
                        key_hash VARCHAR(100) NOT NULL,
                        creation_date TIMESTAMP NOT NULL,
                        last_used_date TIMESTAMP NULL,
                        expiry_date TIMESTAMP NULL
                    )
                    """);
            statement.execute("CREATE INDEX idx_user_public_ssh_key_expiry_date ON user_public_ssh_key (expiry_date)");

            // Row 1: a full key written exactly the way Artemis writes it (getTimestamp/setTimestamp with a UTC calendar).
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO user_public_ssh_key (id, user_id, label, public_key, key_hash, creation_date, last_used_date, expiry_date)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                insert.setLong(1, 1);
                insert.setLong(2, 1);
                insert.setString(3, "full");
                insert.setString(4, "ssh-ed25519 AAAA");
                insert.setString(5, "hash-full");
                insert.setTimestamp(6, Timestamp.from(CREATION_INSTANT), utcCalendar());
                insert.setTimestamp(7, Timestamp.from(LAST_USED_INSTANT), utcCalendar());
                insert.setTimestamp(8, Timestamp.from(EXPIRY_INSTANT), utcCalendar());
                insert.executeUpdate();

                // Row 2: a key with the nullable columns left NULL.
                insert.setLong(1, 2);
                insert.setLong(2, 1);
                insert.setString(3, "nulls");
                insert.setString(4, "ssh-ed25519 BBBB");
                insert.setString(5, "hash-nulls");
                insert.setTimestamp(6, Timestamp.from(CREATION_INSTANT), utcCalendar());
                insert.setNull(7, Types.TIMESTAMP);
                insert.setNull(8, Types.TIMESTAMP);
                insert.executeUpdate();
            }

            valuesBeforeMigration = readApplicationVisibleInstants(connection);
            // Sanity check: the pre-migration read round-trips to what Artemis wrote.
            assertThat(valuesBeforeMigration).as("seeded values read back through the Artemis boundary").containsExactly(CREATION_INSTANT, LAST_USED_INSTANT, EXPIRY_INSTANT);
        }

        // Run the migration on a non-UTC session and keep the connection open for the assertions.
        try (Connection connection = newConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET time_zone = '+02:00'");
            assertSessionZoneIsNonUtc(statement);

            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
            liquibase.update(new Contexts(), new LabelExpression());
            // Liquibase may leave the connection without auto-commit; restore it for the plain JDBC assertions below.
            connection.setAutoCommit(true);

            // The migration must not touch the session time zone (it must convert under the ambient session, not a forced UTC one).
            try (ResultSet resultSet = statement.executeQuery("SELECT @@session.time_zone")) {
                resultSet.next();
                assertThat(resultSet.getString(1)).as("migration leaves the session time zone untouched").isEqualTo(NON_UTC_SESSION_ZONE);
            }

            // The core guarantee: an existing key reads back through the Artemis boundary with exactly the same instant.
            Instant[] valuesAfterMigration = readApplicationVisibleInstants(connection);
            assertThat(valuesAfterMigration).as("application-visible SSH key timestamps are unchanged by the migration").containsExactly(valuesBeforeMigration);

            // All three columns are now DATETIME with millisecond precision, and nullability is preserved.
            assertColumn(connection, "creation_date", "datetime", 3, "NO");
            assertColumn(connection, "last_used_date", "datetime", 3, "YES");
            assertColumn(connection, "expiry_date", "datetime", 3, "YES");

            // The index survives the column rewrite and still targets expiry_date as its first column.
            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT column_name, seq_in_index FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'user_public_ssh_key' AND index_name = 'idx_user_public_ssh_key_expiry_date'
                    """)) {
                assertThat(resultSet.next()).as("expiry_date index preserved").isTrue();
                assertThat(resultSet.getString("column_name")).as("index targets expiry_date").isEqualTo("expiry_date");
                assertThat(resultSet.getInt("seq_in_index")).as("expiry_date is the first index column").isEqualTo(1);
            }

            // NULL values are preserved.
            try (ResultSet resultSet = statement.executeQuery("SELECT last_used_date, expiry_date FROM user_public_ssh_key WHERE id = 2")) {
                resultSet.next();
                resultSet.getObject(1);
                assertThat(resultSet.wasNull()).as("last_used_date stays NULL").isTrue();
                resultSet.getObject(2);
                assertThat(resultSet.wasNull()).as("expiry_date stays NULL").isTrue();
            }

            // A post-2038 expiry date, previously rejected by MySQL TIMESTAMP, can now be stored.
            assertThatCode(() -> statement.execute("""
                    INSERT INTO user_public_ssh_key (id, user_id, label, public_key, key_hash, creation_date, expiry_date)
                    VALUES (3, 1, 'future', 'ssh-ed25519 CCCC', 'hash-future', '2030-06-15 12:00:00.000', '2040-01-01 03:00:00.000')
                    """)).as("post-2038 expiry date is accepted").doesNotThrowAnyException();
            try (ResultSet resultSet = statement.executeQuery("SELECT YEAR(expiry_date) FROM user_public_ssh_key WHERE id = 3")) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).as("post-2038 expiry date is stored").isEqualTo(2040);
            }
        }
    }

    private Connection newConnection() throws Exception {
        return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    private void assertSessionZoneIsNonUtc(Statement statement) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("SELECT @@session.time_zone")) {
            resultSet.next();
            assertThat(resultSet.getString(1)).as("test must run under a non-UTC session to be meaningful").isEqualTo(NON_UTC_SESSION_ZONE);
        }
    }

    /**
     * Reads the three date columns of row 1 through the same boundary Artemis uses, i.e. {@code getTimestamp} with a UTC
     * {@link Calendar} (Hibernate with {@code hibernate.jdbc.time_zone=UTC}). This is what determines the value the
     * application observes, independent of whether the column is a TIMESTAMP or a DATETIME.
     */
    private Instant[] readApplicationVisibleInstants(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT creation_date, last_used_date, expiry_date FROM user_public_ssh_key WHERE id = 1")) {
            resultSet.next();
            return new Instant[] { resultSet.getTimestamp(1, utcCalendar()).toInstant(), resultSet.getTimestamp(2, utcCalendar()).toInstant(),
                    resultSet.getTimestamp(3, utcCalendar()).toInstant() };
        }
    }

    private void assertColumn(Connection connection, String columnName, String expectedDataType, int expectedPrecision, String expectedNullable) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT data_type, datetime_precision, is_nullable FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'user_public_ssh_key' AND column_name = ?
                """)) {
            statement.setString(1, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                assertThat(resultSet.getString("data_type")).as("data type of %s", columnName).isEqualTo(expectedDataType);
                assertThat(resultSet.getInt("datetime_precision")).as("precision of %s", columnName).isEqualTo(expectedPrecision);
                assertThat(resultSet.getString("is_nullable")).as("nullability of %s", columnName).isEqualTo(expectedNullable);
            }
        }
    }
}
