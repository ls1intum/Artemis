package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.scheduling.TaskScheduler;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateMigrationProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateOutboxProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateReconcileProperties;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;

/**
 * Unit tests for {@link WeaviateMigrationStartupService}: the migration must be handed to the {@link TaskScheduler} (background) rather than run inline, must run before the
 * post-migration collection reconciliation, must never let a failure escape (so a broken migration can never block or crash the node), and must keep the outbox
 * dispatcher from writing while it runs (so a migration batch cannot overwrite a newer dispatcher write with older content).
 */
class WeaviateMigrationStartupServiceTest {

    private final WeaviateMigrationService migrationService = mock(WeaviateMigrationService.class);

    private final WeaviateService weaviateService = mock(WeaviateService.class);

    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);

    private final WeaviateOutboxRepository outboxRepository = mock(WeaviateOutboxRepository.class);

    private final SearchableEntityWeaviateService searchableEntityWeaviateService = mock(SearchableEntityWeaviateService.class);

    // A real dispatcher over mocked repositories, so the drain lock the migration holds is the one drains actually use.
    private final WeaviateOutboxDispatcher outboxDispatcher = new WeaviateOutboxDispatcher(outboxRepository, mock(SearchableEntitySyncStateRepository.class),
            searchableEntityWeaviateService, new WeaviateOutboxProperties(5, 100, 10, 300),
            new WeaviateReconcileProperties(false, false, false, List.of(SearchableEntitySchema.TypeValues.EXERCISE), 500, 100, 200, 1000, 5, 100, 100, 0.25));

    // Construct with the production default tuning values (initial delay, max attempts, retry delay).
    private final WeaviateMigrationStartupService startupService = new WeaviateMigrationStartupService(migrationService, weaviateService, taskScheduler,
            new WeaviateMigrationProperties(30, 5, 120), outboxDispatcher);

    @Test
    void schedulesMigrationInBackgroundWithoutRunningItInline() {
        startupService.scheduleMigrationOnStartup();

        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        // The migration must not run on the startup thread; only the scheduled background task may trigger it.
        verifyNoInteractions(migrationService);
    }

    @Test
    void runsMigrationThenReconcilesCollections() {
        captureScheduledTask().run();

        InOrder inOrder = inOrder(migrationService, weaviateService);
        inOrder.verify(migrationService).runPendingMigrations();
        inOrder.verify(weaviateService).ensureAllCollectionsExist();
    }

    @Test
    void swallowsMigrationFailureSoTheNodeIsNeverBlocked() {
        doThrow(new RuntimeException("read timed out")).when(migrationService).runPendingMigrations();

        Runnable task = captureScheduledTask();

        assertThatCode(task::run).doesNotThrowAnyException();
        // Reconciliation is skipped when the migration itself fails, but the failure is contained.
        verify(weaviateService, never()).ensureAllCollectionsExist();
    }

    @Test
    void retriesOnFailureUpToTheBoundedAttemptLimit() {
        // Run each scheduled task synchronously so the retry chain executes within the test.
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        });
        doThrow(new RuntimeException("embedding service cold")).when(migrationService).runPendingMigrations();

        startupService.scheduleMigrationOnStartup();

        // One initial attempt plus retries, capped at the configured max attempts (default 5); it then stops instead of retrying forever.
        verify(migrationService, times(5)).runPendingMigrations();
        verify(weaviateService, never()).ensureAllCollectionsExist();
    }

    @Test
    void pausesOutboxDrainsWhileTheMigrationRuns() throws Exception {
        // A live edit's upsert is waiting in the outbox. The migration checks that the exercise is absent, loads it and then
        // batch-inserts it; a drain landing in between would be overwritten with the older content the migration loaded.
        WeaviateOutboxEntry liveEdit = WeaviateOutboxEntry.forUpsert(SearchableEntitySchema.TypeValues.EXERCISE, 1L, WeaviateOutboxOrigin.LIVE);
        when(outboxRepository.findDueForDispatch(any(), anyInt())).thenReturn(List.of(liveEdit));
        when(searchableEntityWeaviateService.applyOutboxEntry(liveEdit)).thenReturn(Optional.of("v1:" + "a".repeat(64)));

        doAnswer(invocation -> {
            // A drain triggered from another thread (the scheduled tick or the enqueue nudge) mid-migration must apply nothing.
            Thread drainThread = new Thread(outboxDispatcher::drain);
            drainThread.start();
            drainThread.join();
            verifyNoInteractions(outboxRepository, searchableEntityWeaviateService);
            return null;
        }).when(migrationService).runPendingMigrations();

        captureScheduledTask().run();
        verify(migrationService).runPendingMigrations();

        // Once the migration is done, the next drain applies the queued edit on top of the migration's write.
        Thread drainThread = new Thread(outboxDispatcher::drain);
        drainThread.start();
        drainThread.join();
        verify(searchableEntityWeaviateService).applyOutboxEntry(liveEdit);
        verify(outboxRepository).delete(liveEdit);
        assertThat(liveEdit.getAttempts()).isZero();
    }

    @Test
    void releasesTheDrainPauseWhenTheMigrationFails() throws Exception {
        doThrow(new RuntimeException("embedding service cold")).when(migrationService).runPendingMigrations();
        when(outboxRepository.findDueForDispatch(any(), anyInt())).thenReturn(List.of());

        captureScheduledTask().run();

        // Between retries drains must resume, or a failing migration would stall the index for every attempt's retry delay.
        Thread drainThread = new Thread(outboxDispatcher::drain);
        drainThread.start();
        drainThread.join();
        verify(outboxRepository).findDueForDispatch(any(), anyInt());
    }

    private Runnable captureScheduledTask() {
        startupService.scheduleMigrationOnStartup();
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(taskCaptor.capture(), any(Instant.class));
        return taskCaptor.getValue();
    }
}
