package de.tum.cit.aet.artemis.core.config.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
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
 * Verifies the MySQL data migration in {@code 20260730111430_changelog.xml}: rows whose build_plan_configuration or
 * docker_flags exceed the MEDIUMTEXT byte limit are reset to NULL (the default-configuration state, valid for both
 * JSON fields) before the columns are narrowed from LONGTEXT to MEDIUMTEXT, while values within the limit are kept.
 * <p>
 * The changeset is MySQL only (dbms precondition, MARK_RAN elsewhere), so it cannot be exercised by the PostgreSQL
 * test suite. This test therefore starts its own MySQL container and applies the changelog against it via Liquibase.
 * It is skipped when no Docker environment is available.
 */
@EnabledIf("isDockerAvailable")
class BuildConfigColumnMigrationMySqlTest {

    private static final String CHANGELOG = "config/liquibase/changelog/20260730111430_changelog.xml";

    private static final String TABLE = "programming_exercise_build_config";

    // MEDIUMTEXT holds at most 16_777_215 bytes; one byte more must be reset to NULL by the migration.
    private static final int OVERSIZED_BYTES = 16_777_216;

    private static final String SMALL_BUILD_PLAN = "{\"api\":\"v0.0.1\"}";

    private static final String SMALL_DOCKER_FLAGS = "{\"network\":\"none\"}";

    private static MySQLContainer mysql;

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        }
        catch (Throwable error) {
            return false;
        }
    }

    @BeforeAll
    static void startContainer() {
        mysql = new MySQLContainer(DockerImageName.parse("mysql:9"));
        mysql.start();
    }

    @AfterAll
    static void stopContainer() {
        if (mysql != null) {
            mysql.stop();
        }
    }

    @Test
    void shouldResetOversizedValuesToNullAndNarrowColumns() throws Exception {
        seedInitialRows();

        // Sanity-check that the seeded values are genuinely oversized, so the post-migration NULL expectation cannot pass on undersized seed data.
        try (Connection connection = newConnection()) {
            assertThat(octetLength(connection, 1, "build_plan_configuration")).isEqualTo(OVERSIZED_BYTES);
            assertThat(octetLength(connection, 1, "docker_flags")).isEqualTo(OVERSIZED_BYTES);
            assertThat(octetLength(connection, 2, "build_plan_configuration")).isEqualTo(OVERSIZED_BYTES);
            assertThat(octetLength(connection, 3, "docker_flags")).isEqualTo(OVERSIZED_BYTES);
        }

        applyChangelog();

        try (Connection connection = newConnection()) {
            assertThat(columnType(connection, "build_plan_configuration")).isEqualTo("mediumtext");
            assertThat(columnType(connection, "docker_flags")).isEqualTo("mediumtext");

            // Row 1: both columns oversized -> both reset to NULL.
            assertThat(isNull(connection, 1, "build_plan_configuration")).isTrue();
            assertThat(isNull(connection, 1, "docker_flags")).isTrue();

            // Row 2: only build_plan_configuration oversized -> only it is reset; docker_flags is kept unchanged.
            assertThat(isNull(connection, 2, "build_plan_configuration")).isTrue();
            assertThat(value(connection, 2, "docker_flags")).isEqualTo(SMALL_DOCKER_FLAGS);

            // Row 3: only docker_flags oversized -> only it is reset; build_plan_configuration is kept unchanged.
            assertThat(value(connection, 3, "build_plan_configuration")).isEqualTo(SMALL_BUILD_PLAN);
            assertThat(isNull(connection, 3, "docker_flags")).isTrue();

            // Row 4: both within the limit -> both kept unchanged.
            assertThat(value(connection, 4, "build_plan_configuration")).isEqualTo(SMALL_BUILD_PLAN);
            assertThat(value(connection, 4, "docker_flags")).isEqualTo(SMALL_DOCKER_FLAGS);
        }
    }

    private void seedInitialRows() throws Exception {
        try (Connection connection = newConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + TABLE + " (id BIGINT NOT NULL PRIMARY KEY, build_plan_configuration LONGTEXT NULL, docker_flags LONGTEXT NULL)");
            // The oversized values are generated server-side via REPEAT so no multi-megabyte string travels over the wire.
            insertRow(statement, 1, oversized(), oversized());
            insertRow(statement, 2, oversized(), quote(SMALL_DOCKER_FLAGS));
            insertRow(statement, 3, quote(SMALL_BUILD_PLAN), oversized());
            insertRow(statement, 4, quote(SMALL_BUILD_PLAN), quote(SMALL_DOCKER_FLAGS));
        }
    }

    private void insertRow(Statement statement, int id, String buildPlanExpression, String dockerFlagsExpression) throws Exception {
        statement.execute("INSERT INTO " + TABLE + " (id, build_plan_configuration, docker_flags) VALUES (" + id + ", " + buildPlanExpression + ", " + dockerFlagsExpression + ")");
    }

    private static String oversized() {
        return "REPEAT('{', " + OVERSIZED_BYTES + ")";
    }

    private static String quote(String value) {
        return "'" + value + "'";
    }

    private void applyChangelog() throws Exception {
        try (Connection connection = newConnection()) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }
    }

    private String columnType(Connection connection, String column) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT LOWER(DATA_TYPE) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + TABLE
                        + "' AND COLUMN_NAME = '" + column + "'")) {
            assertThat(resultSet.next()).as("column %s must exist", column).isTrue();
            return resultSet.getString(1);
        }
    }

    private boolean isNull(Connection connection, int id, String column) throws Exception {
        // Checks nullness in SQL so an oversized value is never transferred to the client.
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT " + column + " IS NULL FROM " + TABLE + " WHERE id = " + id)) {
            assertThat(resultSet.next()).as("row %d must exist", id).isTrue();
            return resultSet.getBoolean(1);
        }
    }

    private String value(Connection connection, int id, String column) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT " + column + " FROM " + TABLE + " WHERE id = " + id)) {
            assertThat(resultSet.next()).as("row %d must exist", id).isTrue();
            return resultSet.getString(1);
        }
    }

    private long octetLength(Connection connection, int id, String column) throws Exception {
        // Reads only the byte length, never the multi-megabyte value itself.
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT OCTET_LENGTH(" + column + ") FROM " + TABLE + " WHERE id = " + id)) {
            assertThat(resultSet.next()).as("row %d must exist", id).isTrue();
            return resultSet.getLong(1);
        }
    }

    private Connection newConnection() throws Exception {
        return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }
}
