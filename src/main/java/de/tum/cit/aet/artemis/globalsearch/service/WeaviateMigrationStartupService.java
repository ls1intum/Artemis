package de.tum.cit.aet.artemis.globalsearch.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.Instant;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;

/**
 * Runs pending Weaviate schema migrations once, in the background, on the scheduling node only.
 * <p>
 * Migrations move data between Weaviate collections and can be long-running (and, when the embedding backend is slow or cold, can time out per object). Running them inline
 * during bean initialization (as the {@code @PostConstruct} of {@link WeaviateService} previously did) blocks Spring's singleton creation on the main thread and, in turn, the
 * whole UI; it also ran on every node simultaneously. To avoid that, this service:
 * <ul>
 * <li>is gated to the {@code scheduling} node, so only one node in the cluster runs the migration, and</li>
 * <li>hands the work to the {@link TaskScheduler} shortly after startup, so it runs on a background thread and never blocks application startup or request handling, regardless of
 * how long it takes or whether it fails.</li>
 * </ul>
 * Collection creation itself stays in {@link WeaviateService#initializeCollections()} so every node has its collections available before serving search and indexing.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
@Profile(PROFILE_SCHEDULING)
public class WeaviateMigrationStartupService {

    private static final Logger log = LoggerFactory.getLogger(WeaviateMigrationStartupService.class);

    /**
     * Delay before the migration starts, so it does not compete with application startup. The migration runs on a background thread, so this only affects when the one-off
     * background work begins, not whether it blocks anything.
     */
    private static final long MIGRATION_STARTUP_DELAY_SECONDS = 30;

    private final WeaviateMigrationService migrationService;

    private final WeaviateService weaviateService;

    private final TaskScheduler taskScheduler;

    public WeaviateMigrationStartupService(WeaviateMigrationService migrationService, WeaviateService weaviateService, @Qualifier("taskScheduler") TaskScheduler taskScheduler) {
        this.migrationService = migrationService;
        this.weaviateService = weaviateService;
        this.taskScheduler = taskScheduler;
    }

    /**
     * Schedules the migration to run on a background thread shortly after startup.
     * <p>
     * This bean is {@code @Lazy} like all Artemis beans, but it is still instantiated: {@code DeferredEagerBeanInitializer} force-creates every lazy singleton once the application
     * is ready (the same mechanism that previously triggered the inline migration in {@link WeaviateService}), which fires this {@code @PostConstruct}.
     * {@code @EventListener(ApplicationReadyEvent.class)} would not work here, because the bean is instantiated <i>during</i> that deferred initialization, i.e. after the ready
     * event
     * has already been published; {@code @PostConstruct} fires on instantiation regardless of timing. The same pattern is used by other scheduling-node startup services (e.g.
     * {@code AthenaScheduleService}).
     */
    @PostConstruct
    public void scheduleMigrationOnStartup() {
        taskScheduler.schedule(this::runPendingMigrations, Instant.now().plusSeconds(MIGRATION_STARTUP_DELAY_SECONDS));
    }

    /**
     * Runs all pending Weaviate migrations and reconciles collections afterwards. Any failure is logged and swallowed: the migration is best-effort and must never crash the node
     * or block it. Search may return incomplete results until entities are re-indexed if a migration does not complete.
     */
    private void runPendingMigrations() {
        try {
            migrationService.runPendingMigrations();
            // Second pass: recreate any collection a migration dropped in order to apply schema changes.
            weaviateService.ensureAllCollectionsExist();
        }
        catch (Exception exception) {
            log.error("Weaviate migration failed in the background. Search may return incomplete results until entities are re-indexed.", exception);
        }
    }
}
