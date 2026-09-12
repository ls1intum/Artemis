package de.tum.cit.aet.artemis.core.config.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

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
 * {@code timestamp} type has no such ceiling. This test therefore drives Liquibase against a real MySQL
 * container. It seeds the pre-migration schema under a non-UTC session so a missing {@code SET time_zone = '+00:00'}
 * in the changeset would shift the existing values and fail the wall-clock assertion.
 */
@EnabledIf("isDockerAvailable")
class SshKeyDateColumnMigrationMySqlTest {

    private static final String CHANGELOG = "config/liquibase/changelog/20260825000000_changelog.xml";

    private static final String NON_UTC_SESSION_ZONE = "+02:00";

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
    void shouldConvertSshKeyDateColumnsToDatetimePreservingData() throws Exception {
        // The stored UTC epochs of the seeded dates; UNIX_TIMESTAMP is time-zone independent for TIMESTAMP columns.
        long seededCreationEpoch;
        long seededLastUsedEpoch;
        long seededExpiryEpoch;

        // Seed the pre-migration schema and data under a non-UTC session, mirroring an existing MySQL instance.
        try (Connection connection = newConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET time_zone = '+02:00'");
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
            statement.execute("""
                    INSERT INTO user_public_ssh_key (user_id, label, public_key, key_hash, creation_date, last_used_date, expiry_date)
                    VALUES (1, 'full', 'ssh-ed25519 AAAA', 'hash-full', '2030-06-15 12:00:00', '2031-01-02 08:30:00', '2032-03-04 03:00:00')
                    """);
            statement.execute("""
                    INSERT INTO user_public_ssh_key (user_id, label, public_key, key_hash, creation_date, last_used_date, expiry_date)
                    VALUES (2, 'nulls', 'ssh-ed25519 BBBB', 'hash-nulls', '2030-06-15 12:00:00', NULL, NULL)
                    """);
            try (ResultSet resultSet = statement
                    .executeQuery("SELECT UNIX_TIMESTAMP(creation_date), UNIX_TIMESTAMP(last_used_date), UNIX_TIMESTAMP(expiry_date) FROM user_public_ssh_key WHERE id = 1")) {
                resultSet.next();
                seededCreationEpoch = resultSet.getLong(1);
                seededLastUsedEpoch = resultSet.getLong(2);
                seededExpiryEpoch = resultSet.getLong(3);
            }
        }

        // Run the migration on a non-UTC session and keep the connection open for the assertions.
        try (Connection connection = newConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET time_zone = '+02:00'");

            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
            liquibase.update(new Contexts(), new LabelExpression());
            // Liquibase may leave the connection without auto-commit; restore it for the plain JDBC assertions below.
            connection.setAutoCommit(true);

            // The changeset must restore the previous session time zone after the conversion.
            try (ResultSet resultSet = statement.executeQuery("SELECT @@session.time_zone")) {
                resultSet.next();
                assertThat(resultSet.getString(1)).as("session time zone restored after migration").isEqualTo(NON_UTC_SESSION_ZONE);
            }

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

            // The pre-existing values keep their UTC wall-clock: read as UTC, their epochs must match the seeded epochs.
            statement.execute("SET time_zone = '+00:00'");
            try (ResultSet resultSet = statement
                    .executeQuery("SELECT UNIX_TIMESTAMP(creation_date), UNIX_TIMESTAMP(last_used_date), UNIX_TIMESTAMP(expiry_date) FROM user_public_ssh_key WHERE id = 1")) {
                resultSet.next();
                assertThat(resultSet.getLong(1)).as("creation_date keeps its UTC wall-clock").isEqualTo(seededCreationEpoch);
                assertThat(resultSet.getLong(2)).as("last_used_date keeps its UTC wall-clock").isEqualTo(seededLastUsedEpoch);
                assertThat(resultSet.getLong(3)).as("expiry_date keeps its UTC wall-clock").isEqualTo(seededExpiryEpoch);
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
                    INSERT INTO user_public_ssh_key (user_id, label, public_key, key_hash, creation_date, expiry_date)
                    VALUES (3, 'future', 'ssh-ed25519 CCCC', 'hash-future', '2030-06-15 12:00:00.000', '2040-01-01 03:00:00.000')
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
