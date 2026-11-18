package de.tum.cit.aet.artemis.core.config.liquibase;

import java.util.List;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.changelog.ChangeLogHistoryServiceFactory;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.snapshot.SnapshotGeneratorFactory;

/**
 * Applies the Artemis changelog layout, in which a baseline stands in for the changelogs it folded.
 * <p>
 * {@code master.xml} lists every changelog a baseline has already folded, then the baseline, then the
 * migrations written since. On a database that has been upgraded this needs nothing special: the folded
 * changelogs are recorded, so Liquibase skips them, and the baseline marks itself as ran because the
 * schema it would create is already there.
 * <p>
 * An empty database would otherwise execute the whole folded history and then find the baseline
 * redundant, which is correct but is the slow replay the baseline exists to avoid. So this class runs
 * Liquibase's {@code changelog-sync} over the folded history first, which records those changesets
 * without executing them; the baseline then creates the schema in one step.
 * <p>
 * That ordering is deliberate. Because the history comes before the baseline, the changelog is correct
 * whether or not this class is the one applying it -- a harness that builds its own plain
 * {@link SpringLiquibase}, as Zonky's embedded-database provider does, simply gets the slow route. The
 * sync is an optimisation, not something correctness rests on.
 * <p>
 * Both paths therefore end with the same schema and the same set of recorded changesets, which is what
 * the fresh-vs-upgrade and upgrade-from-floor checks in {@code supporting_scripts/liquibase/verify_schema.py}
 * assert on every pull request. Only the {@code EXECTYPE} differs: a sync records everything as
 * {@code EXECUTED} without evaluating preconditions, where an upgrade records {@code MARK_RAN} for the
 * changesets whose preconditions did not hold. Nothing reads that column.
 * <p>
 * The layout and the reasoning behind it are documented in
 * {@code documentation/docs/developer/guidelines/database-migration-consolidation.mdx}.
 */
public class ArtemisSpringLiquibase extends SpringLiquibase {

    private static final Logger log = LoggerFactory.getLogger(ArtemisSpringLiquibase.class);

    /**
     * The manifest of every changelog a baseline has already folded. Kept in step with the
     * {@code history/} include in {@code master.xml}.
     */
    private static final String FOLDED_HISTORY_CHANGELOG = "classpath:config/liquibase/history/master.xml";

    @Override
    protected void performUpdate(Liquibase liquibase) throws LiquibaseException {
        Database database = liquibase.getDatabase();
        if (isEmpty(database)) {
            recordFoldedHistory(database);
        }
        super.performUpdate(liquibase);
    }

    /**
     * Whether this database has never had a changelog applied to it.
     * <p>
     * A missing {@code DATABASECHANGELOG} is the ordinary case. The table can also exist while being
     * empty, because Liquibase creates it before it applies anything and a first attempt may have failed
     * after that point; such a database still needs the folded history recorded.
     *
     * @param database the database Liquibase is about to update
     * @return true when nothing has been applied yet
     * @throws LiquibaseException if the database cannot be inspected, which must stop the startup rather
     *                                than be guessed at: the wrong answer here either re-applies the
     *                                folded history or skips the schema entirely
     */
    private boolean isEmpty(Database database) throws LiquibaseException {
        if (!SnapshotGeneratorFactory.getInstance().hasDatabaseChangeLogTable(database)) {
            return true;
        }
        return database.getRanChangeSetList().isEmpty();
    }

    /**
     * Records every changelog the baseline folded as applied, without running any of it.
     *
     * @param database the empty database being initialised
     * @throws LiquibaseException if the history cannot be recorded
     */
    private void recordFoldedHistory(Database database) throws LiquibaseException {
        log.info("Empty database: recording the folded changelog history from {} without executing it", FOLDED_HISTORY_CHANGELOG);
        // Deliberately not closed: closing a Liquibase closes the database behind it, and that database
        // belongs to the update this runs in front of.
        Liquibase history = new Liquibase(FOLDED_HISTORY_CHANGELOG, createResourceOpener(), database);
        // The same contexts and labels as the update. A changelog-sync applies them exactly as an update
        // does, so a narrower filter here would leave changesets unrecorded that the update then runs.
        // The parameters go across for the same reason: a changelog that resolves a property differently
        // during the sync than during the update is a changelog recorded under the wrong identity.
        if (parameters != null) {
            parameters.forEach(history::setChangeLogParameter);
        }

        Contexts contexts = new Contexts(getContexts());
        LabelExpression labels = new LabelExpression(getLabelFilter());

        try {
            history.changeLogSync(contexts, labels);
            verifyNothingWasLeftUnrecorded(history, contexts, labels);
        }
        catch (LiquibaseException | RuntimeException failure) {
            discardPartialHistory(database, failure);
            throw failure;
        }
        log.info("Recorded the folded changelog history");
    }

    /**
     * Returns the database to the empty state this attempt started from.
     * <p>
     * Each recorded changeset is committed on its own, so a sync that fails partway leaves rows behind.
     * Those rows are what {@link #isEmpty} reads, so leaving them would make the next start believe the
     * history was already recorded: it would skip the sync, and the folded changesets that never got
     * recorded would execute as ordinary migrations. Dropping the changelog tables costs nothing here,
     * because this only runs on a database that had no rows in them a moment ago, and it leaves the next
     * start facing exactly the empty database this one did.
     *
     * @param database the database whose changelog history is to be discarded
     * @param failure  the failure being propagated, which a failure to clean up is attached to
     */
    private void discardPartialHistory(Database database, Exception failure) {
        try {
            ChangeLogHistoryServiceFactory.getInstance().getChangeLogService(database).destroy();
            log.error("Recording the folded changelog history failed; discarded what had been recorded so that the next start retries it");
        }
        catch (Exception cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
            log.error("Recording the folded changelog history failed, and the partial history could not be discarded. "
                    + "Restore the database to an empty state before starting again, or the folded changelogs will execute as migrations.");
        }
    }

    /**
     * Fails the startup unless every folded changeset is now recorded.
     * <p>
     * Liquibase's changelog-sync swallows a per-changeset failure: it logs at FINE and carries on, so a
     * sync that half worked returns normally. That matters here because the emptiness test is "no rows
     * recorded" -- after a partial sync there are rows, so the next start skips the sync entirely,
     * applies the baseline, and then *executes* the folded changesets that were never recorded. Most
     * would be caught by their own preconditions; the ones whose body is a bare data migration would
     * simply run again. Checking the outcome is what turns that into a startup failure instead.
     *
     * @param history  the Liquibase instance the sync ran through
     * @param contexts the contexts the sync ran with
     * @param labels   the labels the sync ran with
     * @throws LiquibaseException if anything is still unrecorded, or if the check itself cannot run
     */
    private void verifyNothingWasLeftUnrecorded(Liquibase history, Contexts contexts, LabelExpression labels) throws LiquibaseException {
        List<ChangeSet> unrecorded = history.listUnrunChangeSets(contexts, labels);
        if (!unrecorded.isEmpty()) {
            String firstFew = unrecorded.stream().limit(5).map(ChangeSet::toString).collect(Collectors.joining(", "));
            throw new LiquibaseException("Recording the folded changelog history left " + unrecorded.size() + " changeset(s) unrecorded, for example: " + firstFew
                    + ". Starting now would execute them against a schema the baseline has already created. Restore the database to an empty state and start again.");
        }
    }

    /**
     * Creates a {@link SpringLiquibase} instance with the correct DataSource configuration.
     * <p>
     * If a dedicated Liquibase DataSource is provided, it is used directly.
     * Otherwise, if the standard DataSource is available, it is used.
     * As a fallback, a new DataSource is created from the provided properties.
     *
     * @param liquibaseDataSource  optional dedicated Liquibase DataSource
     * @param liquibaseProperties  Liquibase-specific properties (URL, user, password)
     * @param dataSource           the primary application DataSource
     * @param dataSourceProperties the DataSource configuration properties
     * @return a configured {@link SpringLiquibase} instance
     */
    public static SpringLiquibase createSpringLiquibase(DataSource liquibaseDataSource, LiquibaseProperties liquibaseProperties, DataSource dataSource,
            DataSourceProperties dataSourceProperties) {

        ArtemisSpringLiquibase liquibase = new ArtemisSpringLiquibase();

        if (liquibaseDataSource != null) {
            liquibase.setDataSource(liquibaseDataSource);
        }
        else if (liquibaseProperties.getUrl() != null) {
            liquibase.setDataSource(createNewDataSource(liquibaseProperties, dataSourceProperties));
        }
        else {
            liquibase.setDataSource(dataSource);
        }

        return liquibase;
    }

    private static DataSource createNewDataSource(LiquibaseProperties liquibaseProperties, DataSourceProperties dataSourceProperties) {
        String url = liquibaseProperties.getUrl();
        String user = liquibaseProperties.getUser() != null ? liquibaseProperties.getUser() : dataSourceProperties.getUsername();
        String password = liquibaseProperties.getPassword() != null ? liquibaseProperties.getPassword() : dataSourceProperties.getPassword();

        return DataSourceBuilder.create().url(url).username(user).password(password).build();
    }
}
