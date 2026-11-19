package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.programming.domain.BranchingSettings;
import de.tum.cit.aet.artemis.programming.domain.BuildConfigAdvancedSettings;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * Runs the changeset that moves the branching settings of the build configurations into the {@code advanced_settings} column against both supported databases, with the
 * kinds of rows that exist in an installation, and reads the stored documents back the way the application does.
 */
class BuildConfigAdvancedSettingsMigrationTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    // MySQL may perform many fsyncs when it first initializes its data directory, which can slow startup on a busy CI runner, so it lives in memory
    @ParameterizedTest
    @ValueSource(strings = { "jdbc:tc:postgresql:18.4-alpine:///build_config_upgrade", "jdbc:tc:mysql:8.4:///build_config_upgrade?TC_TMPFS=/var/lib/mysql:rw" })
    void movesTheBranchingSettingsAndLeavesDefaultsNull(String url) throws Exception {
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            // the columns as the baseline schema defines them
            statement.execute("CREATE TABLE programming_exercise_build_config (id BIGINT PRIMARY KEY, allow_branching BOOLEAN DEFAULT FALSE NOT NULL, branch_regex VARCHAR(128))");

            insert(connection, 1, false, null);
            insert(connection, 2, false, ".*");
            insert(connection, 3, true, null);
            insert(connection, 4, true, ".*");
            insert(connection, 5, true, "feature/.*");
            insert(connection, 6, false, "feature/.*");
            insert(connection, 7, true, "^(fix|feat)\\/\"x\"-ü€'$");
            insert(connection, 8, true, "a".repeat(128));
            insert(connection, 9, true, "");
            insert(connection, 10, false, "");

            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var liquibase = new Liquibase("config/liquibase/changelog/20261027204034_changelog.xml", new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
                // reading has to stay inside: closing Liquibase closes the connection
                assertDocuments(read(statement));
            }
        }
    }

    private static void assertDocuments(Map<Long, BuildConfigAdvancedSettings> documents) {
        assertThat(documents.get(1L)).as("the defaults stay NULL").isNull();
        assertThat(documents.get(2L)).as("the default regex of an exercise without branching stays NULL").isNull();
        assertThat(documents.get(3L)).isEqualTo(new BuildConfigAdvancedSettings(new BranchingSettings(true, null), null));
        assertThat(documents.get(4L)).isEqualTo(new BuildConfigAdvancedSettings(new BranchingSettings(true, ".*"), null));
        assertThat(documents.get(5L)).isEqualTo(new BuildConfigAdvancedSettings(new BranchingSettings(true, "feature/.*"), null));
        assertThat(documents.get(6L)).as("a regex without branching is kept").isEqualTo(new BuildConfigAdvancedSettings(new BranchingSettings(false, "feature/.*"), null));
        assertThat(documents.get(7L)).as("quotes, backslashes and non-ASCII characters survive")
                .isEqualTo(new BuildConfigAdvancedSettings(new BranchingSettings(true, "^(fix|feat)\\/\"x\"-ü€'$"), null));
        assertThat(documents.get(8L)).as("the longest regex survives").isEqualTo(new BuildConfigAdvancedSettings(new BranchingSettings(true, "a".repeat(128)), null));
        assertThat(documents.get(9L)).as("an empty regex stays empty").isEqualTo(new BuildConfigAdvancedSettings(new BranchingSettings(true, ""), null));
        assertThat(documents.get(10L)).isEqualTo(new BuildConfigAdvancedSettings(new BranchingSettings(false, ""), null));
    }

    private static void insert(Connection connection, long id, boolean allowBranching, String branchRegex) throws Exception {
        // a prepared statement, as MySQL would read a backslash in a string literal as an escape
        try (var insert = connection.prepareStatement("INSERT INTO programming_exercise_build_config (id, allow_branching, branch_regex) VALUES (?, ?, ?)")) {
            insert.setLong(1, id);
            insert.setBoolean(2, allowBranching);
            insert.setString(3, branchRegex);
            insert.executeUpdate();
        }
    }

    private static Map<Long, BuildConfigAdvancedSettings> read(Statement statement) throws Exception {
        Map<Long, BuildConfigAdvancedSettings> documents = new HashMap<>();
        try (var resultSet = statement.executeQuery("SELECT id, advanced_settings FROM programming_exercise_build_config")) {
            while (resultSet.next()) {
                String json = resultSet.getString("advanced_settings");
                documents.put(resultSet.getLong("id"), json == null ? null : MAPPER.readValue(json, BuildConfigAdvancedSettings.class));
            }
        }
        return documents;
    }
}
