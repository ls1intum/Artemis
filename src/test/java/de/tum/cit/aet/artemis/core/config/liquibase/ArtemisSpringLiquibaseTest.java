package de.tum.cit.aet.artemis.core.config.liquibase;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Checks what {@link ArtemisSpringLiquibase} left behind on the empty database this test run started from.
 * <p>
 * Every test database is created fresh, so this context went down the fresh-installation path: the folded
 * history recorded without being executed, then the baseline applied. The assertions below are what tells
 * the two paths apart, because a schema alone cannot -- executing the history produces the same tables,
 * just slower, and the point of the baseline is that it does not have to.
 */
class ArtemisSpringLiquibaseTest extends AbstractSpringIntegrationIndependentTest {

    /**
     * A changeset from the folded history that tells the two routes apart.
     * <p>
     * Its precondition is {@code columnExists drag_and_drop_mapping.correct_mappings_order}, and an
     * earlier folded changelog drops that whole table. So replaying the history *executes* this
     * changelog and records it as MARK_RAN, because the precondition no longer holds. A changelog-sync
     * evaluates no preconditions and records it as EXECUTED. Only the sync can produce EXECUTED here,
     * which is what makes this assertion mean something: a changeset that ran either way would pass
     * whichever route the database took.
     */
    private static final String SYNCED_CHANGESET_ID = "20260728223939-02-drop-drag-and-drop-mapping-order";

    /** The first changeset of the current baseline, which does have to run on an empty database. */
    private static final String BASELINE_CHANGESET_ID = "v10-01";

    @Autowired
    private DataSource dataSource;

    @Test
    void recordsTheFoldedHistoryWithoutExecutingIt() throws SQLException {
        Map<String, String> executionTypes = readExecutionTypes();

        assertThat(executionTypes).as("the folded history must be recorded on a fresh installation, or it would be retried on the next start").containsKey(SYNCED_CHANGESET_ID);
        assertThat(executionTypes.get(SYNCED_CHANGESET_ID))
                .as("a folded changeset must be recorded by changelog-sync; MARK_RAN would mean the history was executed and its precondition skipped it").isEqualTo("EXECUTED");
    }

    @Test
    void appliesTheBaselineOnAnEmptyDatabase() throws SQLException {
        assertThat(readExecutionTypes()).as("the baseline is what creates the schema on an empty database").containsEntry(BASELINE_CHANGESET_ID, "EXECUTED");
    }

    @Test
    void recordsTheFoldedHistoryUnderThePathItWasFirstAppliedAt() throws SQLException {
        // logicalFilePath is what lets the changelogs be moved into history/ without every deployed
        // database seeing a new file and running them a second time.
        assertThat(readFileNames()).contains("config/liquibase/changelog/00000000000000_initial_schema.xml");
    }

    private Map<String, String> readExecutionTypes() throws SQLException {
        Map<String, String> executionTypes = new HashMap<>();
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT ID, EXECTYPE FROM DATABASECHANGELOG")) {
            while (result.next()) {
                executionTypes.put(result.getString("ID"), result.getString("EXECTYPE"));
            }
        }
        return executionTypes;
    }

    private Set<String> readFileNames() throws SQLException {
        Set<String> fileNames = new HashSet<>();
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT FILENAME FROM DATABASECHANGELOG")) {
            while (result.next()) {
                fileNames.add(result.getString("FILENAME"));
            }
        }
        return fileNames;
    }
}
