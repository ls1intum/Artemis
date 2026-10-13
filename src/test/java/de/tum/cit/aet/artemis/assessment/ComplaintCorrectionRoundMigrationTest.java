package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * Runs the changeset that gives the manual results without a correction round their round against both supported databases.
 * <p>
 * The results are the ones a complaint about an automatic result left behind before the complaint assigned a round itself:
 * the original result turned into a manual one without a round, and, once the complaint was answered, an answer that got
 * round 0 because a result without a round is not counted when the next round is chosen.
 */
class ComplaintCorrectionRoundMigrationTest {

    @ParameterizedTest
    @ValueSource(strings = { "jdbc:tc:postgresql:18.4-alpine:///complaint_round_upgrade", "jdbc:tc:mysql:8.4:///complaint_round_upgrade" })
    void givesResultsWithoutRoundTheirRoundAndKeepsTheOriginalBeforeItsAnswer(String url) throws Exception {
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE result (id BIGINT PRIMARY KEY, submission_id BIGINT, correction_round SMALLINT, assessment_type VARCHAR(25))");

            // an answered complaint: the original has no round and its answer holds round 0
            insert(statement, 1, 1, null, "SEMI_AUTOMATIC");
            insert(statement, 2, 1, 0, "SEMI_AUTOMATIC");
            // a complaint that was not answered yet
            insert(statement, 3, 2, null, "SEMI_AUTOMATIC");
            // an automatic result nobody complained about
            insert(statement, 4, 3, null, "AUTOMATIC");
            // the two rounds of an exam
            insert(statement, 5, 4, 0, "MANUAL");
            insert(statement, 6, 4, 1, "MANUAL");
            // an automatic result next to a manual one
            insert(statement, 7, 5, null, "AUTOMATIC");
            insert(statement, 8, 5, 0, "MANUAL");
            // an answered complaint that was answered a second time
            insert(statement, 9, 6, null, "SEMI_AUTOMATIC");
            insert(statement, 10, 6, 0, "SEMI_AUTOMATIC");
            insert(statement, 11, 6, 1, "SEMI_AUTOMATIC");
            // a result without a round that follows a manual result of round 0
            insert(statement, 12, 7, 0, "MANUAL");
            insert(statement, 13, 7, null, "SEMI_AUTOMATIC");
            // two results without a round
            insert(statement, 14, 8, null, "SEMI_AUTOMATIC");
            insert(statement, 15, 8, null, "MANUAL");
            // an Athena result and a result without a submission have no round
            insert(statement, 16, 9, null, "AUTOMATIC_ATHENA");
            insert(statement, 17, null, null, "SEMI_AUTOMATIC");
            // the answer of a complaint that is the only result which has a round, next to an Athena result
            insert(statement, 18, 10, null, "AUTOMATIC_ATHENA");
            insert(statement, 19, 10, null, "SEMI_AUTOMATIC");
            insert(statement, 20, 10, 0, "SEMI_AUTOMATIC");

            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var liquibase = new Liquibase("config/liquibase/changelog/20261012151949_changelog.xml", new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
                // reading has to stay inside: closing Liquibase closes the connection
                assertRounds(readRounds(statement));
            }
        }
    }

    private static void assertRounds(Map<Long, Integer> rounds) {
        assertThat(rounds.get(1L)).as("the original of an answered complaint comes first").isEqualTo(0);
        assertThat(rounds.get(2L)).as("the answer of the complaint comes second").isEqualTo(1);
        assertThat(rounds.get(3L)).as("a complaint that was not answered yet gets round 0").isEqualTo(0);
        assertThat(rounds.get(4L)).as("an automatic result keeps no round").isNull();
        assertThat(rounds.get(5L)).as("the first round of an exam is untouched").isEqualTo(0);
        assertThat(rounds.get(6L)).as("the second round of an exam is untouched").isEqualTo(1);
        assertThat(rounds.get(7L)).as("an automatic result keeps no round").isNull();
        assertThat(rounds.get(8L)).as("a manual result next to an automatic one is untouched").isEqualTo(0);
        assertThat(rounds.get(9L)).as("the original of a complaint answered twice comes first").isEqualTo(0);
        assertThat(rounds.get(10L)).as("the first answer follows the original").isEqualTo(1);
        assertThat(rounds.get(11L)).as("the second answer follows the first").isEqualTo(2);
        assertThat(rounds.get(12L)).as("a result that had its round keeps it").isEqualTo(0);
        assertThat(rounds.get(13L)).as("a result without a round follows the manual result before it").isEqualTo(1);
        assertThat(rounds.get(14L)).as("the first of two results without a round gets round 0").isEqualTo(0);
        assertThat(rounds.get(15L)).as("the second of two results without a round gets round 1").isEqualTo(1);
        assertThat(rounds.get(16L)).as("an Athena result keeps no round").isNull();
        assertThat(rounds.get(17L)).as("a result without a submission keeps no round").isNull();
        assertThat(rounds.get(18L)).as("an Athena result next to a complaint keeps no round").isNull();
        assertThat(rounds.get(19L)).as("the original comes before its answer next to an Athena result").isEqualTo(0);
        assertThat(rounds.get(20L)).as("the answer comes after its original next to an Athena result").isEqualTo(1);
    }

    private static void insert(Statement statement, long id, Integer submissionId, Integer correctionRound, String assessmentType) throws Exception {
        statement.execute("INSERT INTO result (id, submission_id, correction_round, assessment_type) VALUES (" + id + ", " + submissionId + ", " + correctionRound + ", '"
                + assessmentType + "')");
    }

    private static Map<Long, Integer> readRounds(Statement statement) throws Exception {
        Map<Long, Integer> rounds = new HashMap<>();
        try (var resultSet = statement.executeQuery("SELECT id, correction_round FROM result")) {
            while (resultSet.next()) {
                Object round = resultSet.getObject("correction_round");
                rounds.put(resultSet.getLong("id"), round == null ? null : resultSet.getInt("correction_round"));
            }
        }
        return rounds;
    }
}
