package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.scheduling.TaskScheduler;

/**
 * Unit tests for {@link WeaviateMigrationStartupService}: the migration must be handed to the {@link TaskScheduler} (background) rather than run inline, must run before the
 * post-migration collection reconciliation, and must never let a failure escape (so a broken migration can never block or crash the node).
 */
class WeaviateMigrationStartupServiceTest {

    private final WeaviateMigrationService migrationService = mock(WeaviateMigrationService.class);

    private final WeaviateService weaviateService = mock(WeaviateService.class);

    private final SearchableEntityReindexService reindexService = mock(SearchableEntityReindexService.class);

    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);

    private WeaviateMigrationStartupService startupService;

    @BeforeEach
    void setUp() {
        startupService = new WeaviateMigrationStartupService(migrationService, weaviateService, reindexService, taskScheduler);
    }

    @Test
    void schedulesMigrationInBackgroundWithoutRunningItInline() {
        startupService.scheduleMigrationOnStartup();

        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        // The migration must not run on the startup thread; only the scheduled background task may trigger it.
        verifyNoInteractions(migrationService);
    }

    @Test
    void runsMigrationThenReconcilesCollectionsWhenAlreadyUpToDate() {
        when(migrationService.runPendingMigrations()).thenReturn(false);

        captureScheduledTask().run();

        InOrder inOrder = inOrder(migrationService, weaviateService);
        inOrder.verify(migrationService).runPendingMigrations();
        inOrder.verify(weaviateService).ensureAllCollectionsExist();
        verify(reindexService, never()).reindexAll();
    }

    @Test
    void triggersReindexAfterMigrationRan() {
        when(migrationService.runPendingMigrations()).thenReturn(true);
        when(reindexService.reindexAll()).thenReturn("courses=1 exercises=2");

        captureScheduledTask().run();

        InOrder inOrder = inOrder(migrationService, weaviateService, reindexService);
        inOrder.verify(migrationService).runPendingMigrations();
        inOrder.verify(weaviateService).ensureAllCollectionsExist();
        inOrder.verify(reindexService).reindexAll();
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
        doThrow(new RuntimeException("embedding backend cold")).when(migrationService).runPendingMigrations();

        startupService.scheduleMigrationOnStartup();

        // One initial attempt plus retries, capped at MAX_MIGRATION_ATTEMPTS (5); it then stops instead of retrying forever.
        verify(migrationService, times(5)).runPendingMigrations();
        verify(weaviateService, never()).ensureAllCollectionsExist();
    }

    private Runnable captureScheduledTask() {
        startupService.scheduleMigrationOnStartup();
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(taskCaptor.capture(), any(Instant.class));
        return taskCaptor.getValue();
    }
}
