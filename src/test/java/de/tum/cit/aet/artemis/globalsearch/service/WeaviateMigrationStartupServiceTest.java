package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;

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

    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);

    private final WeaviateMigrationStartupService startupService = new WeaviateMigrationStartupService(migrationService, weaviateService, taskScheduler);

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

    private Runnable captureScheduledTask() {
        startupService.scheduleMigrationOnStartup();
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(taskCaptor.capture(), any(Instant.class));
        return taskCaptor.getValue();
    }
}
