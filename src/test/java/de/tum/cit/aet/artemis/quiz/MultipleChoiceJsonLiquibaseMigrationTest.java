package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;

class MultipleChoiceJsonLiquibaseMigrationTest {

    private static final String CHANGELOG = "config/liquibase/changelog/20260615120000_changelog.xml";

    private static final int CHANGESETS_THROUGH_DATABASE_SPECIFIC_BACKFILL = 3;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void testPostgreSQLMigrationBackfillsOrderedJsonAndKeepsScalarReferences() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse(postgresImage()).asCompatibleSubstituteFor("postgres"))) {
            postgres.start();
            runSuccessfulMigrationBackfillTest(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), DatabaseKind.POSTGRESQL);
        }
    }

    @Test
    void testMySQLMigrationBackfillsOrderedJsonAndKeepsScalarReferences() throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse(mysqlImage()).asCompatibleSubstituteFor("mysql"))) {
            mysql.start();
            runSuccessfulMigrationBackfillTest(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword(), DatabaseKind.MYSQL);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wrongNextComponentIdUpdates")
    void testPostgreSQLVerifyHaltsOnWrongNextComponentId(String ignoredCaseName, String corruptSql) throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse(postgresImage()).asCompatibleSubstituteFor("postgres"))) {
            postgres.start();
            assertVerifyHaltsOnInvalidNextComponentId(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), DatabaseKind.POSTGRESQL, corruptSql);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("postgresqlVerifyCorruptions")
    void testPostgreSQLVerifyHaltsOnCorruptBackfilledData(String ignoredCaseName, String corruptSql) throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse(postgresImage()).asCompatibleSubstituteFor("postgres"))) {
            postgres.start();
            assertVerifyHaltsAfterBackfillCorruption(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), DatabaseKind.POSTGRESQL, corruptSql);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wrongNextComponentIdUpdates")
    void testMySQLVerifyHaltsOnWrongNextComponentId(String ignoredCaseName, String corruptSql) throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse(mysqlImage()).asCompatibleSubstituteFor("mysql"))) {
            mysql.start();
            assertVerifyHaltsOnInvalidNextComponentId(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword(), DatabaseKind.MYSQL, corruptSql);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mysqlVerifyCorruptions")
    void testMySQLVerifyHaltsOnCorruptBackfilledData(String ignoredCaseName, String corruptSql) throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse(mysqlImage()).asCompatibleSubstituteFor("mysql"))) {
            mysql.start();
            assertVerifyHaltsAfterBackfillCorruption(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword(), DatabaseKind.MYSQL, corruptSql);
        }
    }

    @Test
    void testH2MigrationInitializesDisposableJsonColumns() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:multiple-choice-json-migration;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            createLegacySchema(connection, DatabaseKind.H2);
            execute(connection, List.of("INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)"));
        }

        runLiquibase(jdbcUrl, "", "");

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            assertThat(tableExists(connection, "answer_option")).isFalse();
            assertThat(OBJECT_MAPPER.readTree(querySingleObject(connection, "SELECT answer_options FROM quiz_question WHERE id = 1").toString())).isEmpty();
            assertThat(queryLong(connection, "SELECT next_component_id FROM quiz_question WHERE id = 1")).isEqualTo(1L);
            assertThat(columnExists(connection, "quiz_question", "version")).isTrue();
        }
    }

    @Test
    void testPostgreSQLMigrationHaltsOnCorruptLegacyAnswerOptions() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse(postgresImage()).asCompatibleSubstituteFor("postgres"))) {
            postgres.start();
            try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                createLegacySchema(connection, DatabaseKind.POSTGRESQL);
                insertCorruptLegacyData(connection);
            }

            assertThatThrownBy(() -> runLiquibase(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())).isInstanceOf(LiquibaseException.class)
                    .hasMessageContaining("Cannot migrate answer_option");

            try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                assertThat(tableExists(connection, "answer_option")).isTrue();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidLegacyAnswerOptionRows")
    void testMigrationHaltsOnInvalidLegacyAnswerOptionRows(String ignoredCaseName, List<String> legacyDataSql) throws Exception {
        assertMigrationHaltsOnInvalidLegacyData(legacyDataSql);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidLegacyAnswerOptionOrders")
    void testMigrationHaltsOnInvalidLegacyAnswerOptionOrders(String ignoredCaseName, List<String> legacyDataSql) throws Exception {
        assertMigrationHaltsOnInvalidLegacyData(legacyDataSql);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidLegacySelectedOptionReferences")
    void testMigrationHaltsOnInvalidLegacySelectedOptionReferences(String ignoredCaseName, List<String> legacyDataSql) throws Exception {
        assertMigrationHaltsOnInvalidLegacyData(legacyDataSql);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidLegacyAnswerCounterReferences")
    void testMigrationHaltsOnInvalidLegacyAnswerCounterReferences(String ignoredCaseName, List<String> legacyDataSql) throws Exception {
        assertMigrationHaltsOnInvalidLegacyData(legacyDataSql);
    }

    private static void assertMigrationHaltsOnInvalidLegacyData(List<String> legacyDataSql) throws Exception {
        String jdbcUrl = "jdbc:h2:mem:invalid-legacy-answer-options-" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            createLegacySchemaForPreflightTest(connection);
            execute(connection, legacyDataSql);
        }

        assertThatThrownBy(() -> runLiquibase(jdbcUrl, "", "")).isInstanceOf(LiquibaseException.class).hasMessageContaining("Cannot migrate answer_option");

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            assertThat(tableExists(connection, "answer_option")).isTrue();
        }
    }

    private static Stream<Arguments> invalidLegacyAnswerOptionRows() {
        return Stream.of(Arguments.of("answer option without question id", List.of(
                "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (11, NULL, 'Alpha', 'Hint A', 'Explanation A', TRUE, FALSE, 0)")),
                Arguments.of("answer option references missing question", List.of(
                        "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (11, 999, 'Alpha', 'Hint A', 'Explanation A', TRUE, FALSE, 0)")),
                Arguments.of("answer option belongs to non-multiple-choice question", List.of(
                        "INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'DND', 501)",
                        "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (11, 1, 'Alpha', 'Hint A', 'Explanation A', TRUE, FALSE, 0)")),
                Arguments.of("answer option without order", List.of("INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)",
                        "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (11, 1, 'Alpha', 'Hint A', 'Explanation A', TRUE, FALSE, NULL)")),
                Arguments.of("answer option with negative order", List.of("INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)",
                        "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (11, 1, 'Alpha', 'Hint A', 'Explanation A', TRUE, FALSE, -1)")),
                Arguments.of("answer option without text", List.of("INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)",
                        "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (11, 1, NULL, 'Hint A', 'Explanation A', TRUE, FALSE, 0)")),
                Arguments.of("answer option with blank text", List.of("INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)",
                        "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (11, 1, '   ', 'Hint A', 'Explanation A', TRUE, FALSE, 0)")),
                Arguments.of("answer option without correctness flag", List.of("INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)",
                        "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (11, 1, 'Alpha', 'Hint A', 'Explanation A', NULL, FALSE, 0)")),
                Arguments.of("answer option without invalid flag", List.of("INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)",
                        "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (11, 1, 'Alpha', 'Hint A', 'Explanation A', TRUE, NULL, 0)")));
    }

    private static Stream<Arguments> invalidLegacyAnswerOptionOrders() {
        return Stream.of(Arguments.of("answer option order starts at one", List.of(
                "INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)",
                "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (11, 1, 'Alpha', 'Hint A', 'Explanation A', TRUE, FALSE, 1)")),
                Arguments.of("answer option order has gap", List.of("INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)",
                        "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (11, 1, 'Alpha', 'Hint A', 'Explanation A', TRUE, FALSE, 0)",
                        "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (12, 1, 'Beta', 'Hint B', 'Explanation B', FALSE, FALSE, 2)")),
                Arguments.of("answer option order has duplicate", List.of("INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)",
                        "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (11, 1, 'Alpha', 'Hint A', 'Explanation A', TRUE, FALSE, 0)",
                        "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (12, 1, 'Beta', 'Hint B', 'Explanation B', FALSE, FALSE, 0)")));
    }

    private static Stream<Arguments> invalidLegacySelectedOptionReferences() {
        return Stream.of(Arguments.of("selected option without submitted answer", List.of(
                "INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)",
                "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (11, 1, 'Alpha', 'Hint A', 'Explanation A', TRUE, FALSE, 0)",
                "INSERT INTO multiple_choice_submitted_answer_selected_options (multiple_choice_submitted_answers_id, selected_options_id) VALUES (1001, 11)")),
                Arguments.of("selected option belongs to non-multiple-choice submitted answer", List.of(
                        "INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)",
                        "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (11, 1, 'Alpha', 'Hint A', 'Explanation A', TRUE, FALSE, 0)",
                        "INSERT INTO submitted_answer (id, discriminator, quiz_question_id) VALUES (1001, 'DND', 1)",
                        "INSERT INTO multiple_choice_submitted_answer_selected_options (multiple_choice_submitted_answers_id, selected_options_id) VALUES (1001, 11)")),
                Arguments.of("selected option references missing answer option",
                        List.of("INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)",
                                "INSERT INTO submitted_answer (id, discriminator, quiz_question_id) VALUES (1001, 'MC', 1)",
                                "INSERT INTO multiple_choice_submitted_answer_selected_options (multiple_choice_submitted_answers_id, selected_options_id) VALUES (1001, 11)")),
                Arguments.of("selected option belongs to different question", List.of(
                        "INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)",
                        "INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (2, 'MC', 502)",
                        "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (11, 2, 'Alpha', 'Hint A', 'Explanation A', TRUE, FALSE, 0)",
                        "INSERT INTO submitted_answer (id, discriminator, quiz_question_id) VALUES (1001, 'MC', 1)",
                        "INSERT INTO multiple_choice_submitted_answer_selected_options (multiple_choice_submitted_answers_id, selected_options_id) VALUES (1001, 11)")));
    }

    private static Stream<Arguments> invalidLegacyAnswerCounterReferences() {
        return Stream.of(
                Arguments.of("answer counter without answer id",
                        List.of("INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)",
                                "INSERT INTO quiz_statistic_counter (id, discriminator, multiple_choice_question_statistic_id, answer_id) VALUES (2001, 'AC', 501, NULL)")),
                Arguments.of("answer counter without matching question statistic",
                        List.of("INSERT INTO quiz_statistic_counter (id, discriminator, multiple_choice_question_statistic_id, answer_id) VALUES (2001, 'AC', 501, 11)")),
                Arguments.of("answer counter belongs to non-multiple-choice question",
                        List.of("INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'DND', 501)",
                                "INSERT INTO quiz_statistic_counter (id, discriminator, multiple_choice_question_statistic_id, answer_id) VALUES (2001, 'AC', 501, 11)")),
                Arguments.of("answer counter references missing answer option",
                        List.of("INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)",
                                "INSERT INTO quiz_statistic_counter (id, discriminator, multiple_choice_question_statistic_id, answer_id) VALUES (2001, 'AC', 501, 11)")),
                Arguments.of("answer counter references option from different question", List.of(
                        "INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)",
                        "INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (2, 'MC', 502)",
                        "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (11, 2, 'Alpha', 'Hint A', 'Explanation A', TRUE, FALSE, 0)",
                        "INSERT INTO quiz_statistic_counter (id, discriminator, multiple_choice_question_statistic_id, answer_id) VALUES (2001, 'AC', 501, 11)")),
                Arguments.of("non-answer counter has answer id",
                        List.of("INSERT INTO quiz_statistic_counter (id, discriminator, multiple_choice_question_statistic_id, answer_id) VALUES (2001, 'DC', NULL, 11)")));
    }

    private static Stream<Arguments> wrongNextComponentIdUpdates() {
        return Stream.of(Arguments.of("next component id ignores existing answer options", "UPDATE quiz_question SET next_component_id = 12 WHERE id = 1"),
                Arguments.of("next component id for empty multiple-choice question is not one", "UPDATE quiz_question SET next_component_id = 2 WHERE id = 2"));
    }

    private static Stream<Arguments> postgresqlVerifyCorruptions() {
        return Stream.of(Arguments.of("answer option JSON count does not match legacy rows",
                "UPDATE quiz_question SET answer_options = answer_options || jsonb_build_array(jsonb_build_object('id', 99, 'text', 'Extra', 'hint', 'Hint X', 'explanation', 'Explanation X', 'isCorrect', true, 'invalid', false)) WHERE id = 1"),
                Arguments.of("answer option JSON content does not match legacy row",
                        "UPDATE quiz_question SET answer_options = jsonb_set(answer_options, '{0,text}', to_jsonb('Changed'::text)) WHERE id = 1"),
                Arguments.of("selected option id is missing from question JSON",
                        "UPDATE multiple_choice_submitted_answer_selected_options SET selected_options_id = 99 WHERE multiple_choice_submitted_answers_id = 1001"),
                Arguments.of("answer counter id is missing from question JSON", "UPDATE quiz_statistic_counter SET answer_id = 99 WHERE id = 2001"));
    }

    private static Stream<Arguments> mysqlVerifyCorruptions() {
        return Stream.of(Arguments.of("answer option JSON count does not match legacy rows",
                "UPDATE quiz_question SET answer_options = JSON_ARRAY_APPEND(answer_options, '$', JSON_OBJECT('id', 99, 'text', 'Extra', 'hint', 'Hint X', 'explanation', 'Explanation X', 'isCorrect', JSON_EXTRACT('true', '$'), 'invalid', JSON_EXTRACT('false', '$'))) WHERE id = 1"),
                Arguments.of("answer option JSON content does not match legacy row",
                        "UPDATE quiz_question SET answer_options = JSON_SET(answer_options, '$[0].text', 'Changed') WHERE id = 1"),
                Arguments.of("selected option id is missing from question JSON",
                        "UPDATE multiple_choice_submitted_answer_selected_options SET selected_options_id = 99 WHERE multiple_choice_submitted_answers_id = 1001"),
                Arguments.of("answer counter id is missing from question JSON", "UPDATE quiz_statistic_counter SET answer_id = 99 WHERE id = 2001"));
    }

    private static void runSuccessfulMigrationBackfillTest(String jdbcUrl, String username, String password, DatabaseKind databaseKind) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            createLegacySchema(connection, databaseKind);
            insertValidLegacyData(connection);
        }

        runLiquibase(jdbcUrl, username, password);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            assertMigratedLegacyData(connection);
        }
    }

    private static void assertVerifyHaltsOnInvalidNextComponentId(String jdbcUrl, String username, String password, DatabaseKind databaseKind, String corruptSql) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            createLegacySchema(connection, databaseKind);
            insertValidLegacyData(connection);
        }

        runLiquibase(jdbcUrl, username, password, CHANGESETS_THROUGH_DATABASE_SPECIFIC_BACKFILL);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            assertThat(tableExists(connection, "answer_option")).isTrue();
            execute(connection, List.of(corruptSql));
        }

        assertThatThrownBy(() -> runLiquibase(jdbcUrl, username, password)).isInstanceOf(LiquibaseException.class).hasMessageContaining(verifyFailureMessage(databaseKind))
                .hasMessageContaining("next component IDs");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            assertThat(tableExists(connection, "answer_option")).isTrue();
        }
    }

    private static void assertVerifyHaltsAfterBackfillCorruption(String jdbcUrl, String username, String password, DatabaseKind databaseKind, String corruptSql) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            createLegacySchemaForPreflightTest(connection);
            insertValidLegacyData(connection);
        }

        runLiquibase(jdbcUrl, username, password, CHANGESETS_THROUGH_DATABASE_SPECIFIC_BACKFILL);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            assertThat(tableExists(connection, "answer_option")).isTrue();
            execute(connection, List.of(corruptSql));
        }

        assertThatThrownBy(() -> runLiquibase(jdbcUrl, username, password)).isInstanceOf(LiquibaseException.class).hasMessageContaining(verifyFailureMessage(databaseKind));

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            assertThat(tableExists(connection, "answer_option")).isTrue();
        }
    }

    private static void runLiquibase(String jdbcUrl, String username, String password) throws SQLException, LiquibaseException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }
    }

    private static void runLiquibase(String jdbcUrl, String username, String password, int changesToApply) throws SQLException, LiquibaseException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(changesToApply, new Contexts(), new LabelExpression());
            }
        }
    }

    private static String verifyFailureMessage(DatabaseKind databaseKind) {
        return switch (databaseKind) {
            case POSTGRESQL -> "PostgreSQL answer option JSON backfill did not preserve content, order, IDs, next component IDs, or references.";
            case MYSQL -> "MySQL answer option JSON backfill did not preserve content, order, IDs, next component IDs, or references.";
            case H2 -> throw new IllegalArgumentException("H2 does not run an answer option verify changeSet.");
        };
    }

    private static void createLegacySchema(Connection connection, DatabaseKind databaseKind) throws SQLException {
        String booleanType = databaseKind == DatabaseKind.POSTGRESQL ? "BOOLEAN" : "BOOLEAN";
        String answerOptionQuestionFk = quoteIdentifier(databaseKind, "FKfqeqisl0e28xp3yn9bmlgkhej");
        String selectedOptionsFk = quoteIdentifier(databaseKind, "FK87gmes7g3ad3qf3wmx3lu0iq0");
        String answerCounterFk = quoteIdentifier(databaseKind, "FKg7hjug3wu6icklf6gbiqs4n18");
        String answerCounterUniqueIndex = switch (databaseKind) {
            case POSTGRESQL -> "uc_statistic_counteranswer_id_col";
            case MYSQL -> "`UC_STATISTIC_COUNTERANSWER_ID_COL`";
            case H2 -> "UC_STATISTIC_COUNTERANSWER_ID_COL";
        };
        execute(connection, List.of("CREATE TABLE quiz_question (id BIGINT PRIMARY KEY, discriminator VARCHAR(31) NOT NULL, quiz_question_statistic_id BIGINT)",
                "CREATE TABLE submitted_answer (id BIGINT PRIMARY KEY, discriminator VARCHAR(31) NOT NULL, quiz_question_id BIGINT NOT NULL)",
                "CREATE TABLE answer_option (id BIGINT PRIMARY KEY, question_id BIGINT NOT NULL, text VARCHAR(255), hint VARCHAR(255), explanation VARCHAR(500), is_correct "
                        + booleanType + ", invalid " + booleanType + ", answer_options_order INT)",
                "CREATE INDEX " + answerOptionQuestionFk + " ON answer_option(question_id)",
                "ALTER TABLE answer_option ADD CONSTRAINT " + answerOptionQuestionFk + " FOREIGN KEY (question_id) REFERENCES quiz_question(id)",
                "CREATE TABLE multiple_choice_submitted_answer_selected_options (multiple_choice_submitted_answers_id BIGINT NOT NULL, selected_options_id BIGINT NOT NULL)",
                "CREATE INDEX " + selectedOptionsFk + " ON multiple_choice_submitted_answer_selected_options(selected_options_id)",
                "ALTER TABLE multiple_choice_submitted_answer_selected_options ADD CONSTRAINT " + selectedOptionsFk
                        + " FOREIGN KEY (selected_options_id) REFERENCES answer_option(id)",
                "CREATE TABLE quiz_statistic_counter (id BIGINT PRIMARY KEY, discriminator VARCHAR(31) NOT NULL, multiple_choice_question_statistic_id BIGINT, answer_id BIGINT)",
                "CREATE UNIQUE INDEX " + answerCounterUniqueIndex + " ON quiz_statistic_counter(answer_id)",
                "ALTER TABLE quiz_statistic_counter ADD CONSTRAINT " + answerCounterFk + " FOREIGN KEY (answer_id) REFERENCES answer_option(id)"));
    }

    private static void createLegacySchemaForPreflightTest(Connection connection) throws SQLException {
        execute(connection, List.of("CREATE TABLE quiz_question (id BIGINT PRIMARY KEY, discriminator VARCHAR(31) NOT NULL, quiz_question_statistic_id BIGINT)",
                "CREATE TABLE submitted_answer (id BIGINT PRIMARY KEY, discriminator VARCHAR(31) NOT NULL, quiz_question_id BIGINT NOT NULL)",
                "CREATE TABLE answer_option (id BIGINT PRIMARY KEY, question_id BIGINT, text VARCHAR(255), hint VARCHAR(255), explanation VARCHAR(500), is_correct BOOLEAN, invalid BOOLEAN, answer_options_order INT)",
                "CREATE TABLE multiple_choice_submitted_answer_selected_options (multiple_choice_submitted_answers_id BIGINT NOT NULL, selected_options_id BIGINT NOT NULL)",
                "CREATE TABLE quiz_statistic_counter (id BIGINT PRIMARY KEY, discriminator VARCHAR(31) NOT NULL, multiple_choice_question_statistic_id BIGINT, answer_id BIGINT)"));
    }

    private static void insertValidLegacyData(Connection connection) throws SQLException {
        execute(connection, List.of("INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)",
                "INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (2, 'MC', 502)",
                "INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (3, 'DND', 503)",
                "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (11, 1, 'Alpha', 'Hint A', 'Explanation A', TRUE, FALSE, 0)",
                "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (12, 1, 'Beta', 'Hint B', 'Explanation B', FALSE, TRUE, 1)",
                "INSERT INTO submitted_answer (id, discriminator, quiz_question_id) VALUES (1001, 'MC', 1)",
                "INSERT INTO multiple_choice_submitted_answer_selected_options (multiple_choice_submitted_answers_id, selected_options_id) VALUES (1001, 12)",
                "INSERT INTO quiz_statistic_counter (id, discriminator, multiple_choice_question_statistic_id, answer_id) VALUES (2001, 'AC', 501, 11)"));
    }

    private static void insertCorruptLegacyData(Connection connection) throws SQLException {
        execute(connection, List.of("INSERT INTO quiz_question (id, discriminator, quiz_question_statistic_id) VALUES (1, 'MC', 501)",
                "INSERT INTO answer_option (id, question_id, text, hint, explanation, is_correct, invalid, answer_options_order) VALUES (11, 1, 'Alpha', 'Hint A', 'Explanation A', TRUE, FALSE, 1)"));
    }

    private static void assertMigratedLegacyData(Connection connection) throws Exception {
        assertThat(tableExists(connection, "answer_option")).isFalse();
        assertThat(columnExists(connection, "quiz_question", "answer_options")).isTrue();
        assertThat(columnExists(connection, "quiz_question", "next_component_id")).isTrue();
        assertThat(columnExists(connection, "quiz_question", "version")).isTrue();

        JsonNode migratedOptions = OBJECT_MAPPER.readTree(querySingleObject(connection, "SELECT answer_options FROM quiz_question WHERE id = 1").toString());
        assertThat(migratedOptions).hasSize(2);
        assertThat(migratedOptions.get(0).get("id").asLong()).isEqualTo(11L);
        assertThat(migratedOptions.get(0).get("text").asText()).isEqualTo("Alpha");
        assertThat(migratedOptions.get(0).get("hint").asText()).isEqualTo("Hint A");
        assertThat(migratedOptions.get(0).get("explanation").asText()).isEqualTo("Explanation A");
        assertThat(migratedOptions.get(0).get("isCorrect").asBoolean()).isTrue();
        assertThat(migratedOptions.get(0).get("invalid").asBoolean()).isFalse();
        assertThat(migratedOptions.get(1).get("id").asLong()).isEqualTo(12L);
        assertThat(migratedOptions.get(1).get("text").asText()).isEqualTo("Beta");
        assertThat(migratedOptions.get(1).get("isCorrect").asBoolean()).isFalse();
        assertThat(migratedOptions.get(1).get("invalid").asBoolean()).isTrue();

        assertThat(queryLong(connection, "SELECT next_component_id FROM quiz_question WHERE id = 1")).isEqualTo(13L);
        assertThat(OBJECT_MAPPER.readTree(querySingleObject(connection, "SELECT answer_options FROM quiz_question WHERE id = 2").toString())).isEmpty();
        assertThat(queryLong(connection, "SELECT next_component_id FROM quiz_question WHERE id = 2")).isEqualTo(1L);
        assertThat(queryLong(connection, "SELECT selected_options_id FROM multiple_choice_submitted_answer_selected_options WHERE multiple_choice_submitted_answers_id = 1001"))
                .isEqualTo(12L);
        assertThat(queryLong(connection, "SELECT answer_id FROM quiz_statistic_counter WHERE id = 2001")).isEqualTo(11L);
        assertThat(uniqueConstraintExists(connection, "uc_mc_statistic_counter_question_answer")).isTrue();
    }

    private static void execute(Connection connection, List<String> sqlStatements) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : sqlStatements) {
                statement.execute(sql);
            }
        }
    }

    private static Object querySingleObject(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            Object value = resultSet.getObject(1);
            if (value instanceof byte[] bytes) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            return value;
        }
    }

    private static Long queryLong(Connection connection, String sql) throws SQLException {
        Object value = querySingleObject(connection, sql);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        return existsInInformationSchema(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_name) = lower('" + tableName + "')");
    }

    private static boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        return existsInInformationSchema(connection,
                "SELECT COUNT(*) FROM information_schema.columns WHERE lower(table_name) = lower('" + tableName + "') AND lower(column_name) = lower('" + columnName + "')");
    }

    private static boolean uniqueConstraintExists(Connection connection, String constraintName) throws SQLException {
        return existsInInformationSchema(connection,
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE lower(constraint_name) = lower('" + constraintName + "') AND constraint_type = 'UNIQUE'");
    }

    private static boolean existsInInformationSchema(Connection connection, String sql) throws SQLException {
        return queryLong(connection, sql) > 0;
    }

    private static String postgresImage() {
        return "docker.io/library/postgres:" + System.getProperty("postgres.version", "18.4") + System.getProperty("postgres.image.variant", "-alpine");
    }

    private static String mysqlImage() {
        return "docker.io/library/mysql:" + System.getProperty("mysql.version", "9.7.0");
    }

    private static String quoteIdentifier(DatabaseKind databaseKind, String identifier) {
        return switch (databaseKind) {
            case POSTGRESQL -> "\"" + identifier + "\"";
            case MYSQL -> "`" + identifier + "`";
            case H2 -> identifier;
        };
    }

    private enum DatabaseKind {
        POSTGRESQL, MYSQL, H2
    }
}
