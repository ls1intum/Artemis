package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.service.BuildContainerCacheCleanupService.CleanupOutcome;
import de.tum.cit.aet.artemis.buildagent.service.BuildContainerCacheCleanupService.PruneStats;

/**
 * Unit tests for {@link BuildContainerCacheCleanupService}. The pause/resume hand-off to the queue processing
 * service is verified with Mockito; the file-pruning logic is exercised against real files in a {@link TempDir}.
 */
class BuildContainerCacheCleanupServiceTest {

    @TempDir
    Path mavenCache;

    @TempDir
    Path gradleCache;

    private BuildAgentConfiguration buildAgentConfiguration;

    private SharedQueueProcessingService sharedQueueProcessingService;

    private BuildContainerCacheCleanupService service;

    @BeforeEach
    void setUp() {
        buildAgentConfiguration = mock(BuildAgentConfiguration.class);
        sharedQueueProcessingService = mock(SharedQueueProcessingService.class);

        // Default: both caches configured, not read-only, pause is granted.
        when(buildAgentConfiguration.mavenCacheHostPath()).thenReturn(mavenCache);
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(gradleCache);
        when(buildAgentConfiguration.isBuildContainerCacheReadOnly()).thenReturn(false);
        lenient().when(sharedQueueProcessingService.pauseForMaintenance()).thenReturn(true);

        service = new BuildContainerCacheCleanupService(buildAgentConfiguration, sharedQueueProcessingService);
        service.setCleanupEnabled(true);
        service.setMaxAgeDays(30);
        service.setMavenMaxSize(DataSize.ofGigabytes(3));
        service.setGradleMaxSize(DataSize.ofGigabytes(6));
        service.setLowWatermarkRatio(0.75);
    }

    @Test
    void disabledFlagShortCircuitsBeforePausing() {
        service.setCleanupEnabled(false);

        CleanupOutcome outcome = service.runCleanup();

        assertThat(outcome.wasSkipped()).isTrue();
        assertThat(outcome.skippedReason()).isEqualTo("disabled");
        verify(sharedQueueProcessingService, never()).pauseForMaintenance();
        verify(sharedQueueProcessingService, never()).resumeFromMaintenance();
    }

    @Test
    void readOnlyCacheShortCircuitsBeforePausing() {
        when(buildAgentConfiguration.isBuildContainerCacheReadOnly()).thenReturn(true);

        CleanupOutcome outcome = service.runCleanup();

        assertThat(outcome.wasSkipped()).isTrue();
        assertThat(outcome.skippedReason()).isEqualTo("read-only");
        verify(sharedQueueProcessingService, never()).pauseForMaintenance();
    }

    @Test
    void noCachePathsConfiguredSkipsWithoutPausing() {
        when(buildAgentConfiguration.mavenCacheHostPath()).thenReturn(null);
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);

        CleanupOutcome outcome = service.runCleanup();

        assertThat(outcome.wasSkipped()).isTrue();
        assertThat(outcome.skippedReason()).isEqualTo("no-targets");
        verify(sharedQueueProcessingService, never()).pauseForMaintenance();
    }

    @Test
    void pauseAlreadyHeldDoesNotResumeOnBehalfOfOthers() {
        when(sharedQueueProcessingService.pauseForMaintenance()).thenReturn(false);

        CleanupOutcome outcome = service.runCleanup();

        assertThat(outcome.wasSkipped()).isTrue();
        assertThat(outcome.skippedReason()).isEqualTo("already-paused");
        verify(sharedQueueProcessingService, times(1)).pauseForMaintenance();
        verify(sharedQueueProcessingService, never()).resumeFromMaintenance();
    }

    @Test
    void agePhaseDeletesFilesOlderThanThresholdAndKeepsRecentOnes() throws IOException {
        Path oldFile = touchFileWithAtime(mavenCache.resolve("group/old.jar"), 1024, daysAgo(45));
        Path freshFile = touchFileWithAtime(mavenCache.resolve("group/fresh.jar"), 2048, daysAgo(5));

        CleanupOutcome outcome = service.runCleanup();

        PruneStats mavenStats = statsFor(outcome, mavenCache);
        assertThat(mavenStats.ageDeletedFiles()).isEqualTo(1);
        assertThat(mavenStats.ageDeletedBytes()).isEqualTo(1024);
        assertThat(mavenStats.sizeDeletedFiles()).isZero();
        assertThat(oldFile).doesNotExist();
        assertThat(freshFile).exists();
        verify(sharedQueueProcessingService, times(1)).pauseForMaintenance();
        verify(sharedQueueProcessingService, times(1)).resumeFromMaintenance();
    }

    @Test
    void sizePhaseEvictsLeastRecentlyUsedUntilLowWatermark() throws IOException {
        // High watermark 1 MB, low 0.5 MB (ratio 0.5 to make the maths easy).
        // 4 files × 0.5 MB each = 2 MB > high. Expect oldest two to go, leaving 1 MB which is still > low (0.5 MB)
        // — so we keep evicting until total ≤ 0.5 MB. That means 3 deletions in total, keeping just the newest.
        service.setMavenMaxSize(DataSize.ofBytes(1_000_000));
        service.setLowWatermarkRatio(0.5);
        service.setMaxAgeDays(365); // disable age-pruning for this test

        Path f1 = touchFileWithAtime(mavenCache.resolve("a/f1.jar"), 500_000, daysAgo(20));
        Path f2 = touchFileWithAtime(mavenCache.resolve("a/f2.jar"), 500_000, daysAgo(15));
        Path f3 = touchFileWithAtime(mavenCache.resolve("a/f3.jar"), 500_000, daysAgo(10));
        Path f4 = touchFileWithAtime(mavenCache.resolve("a/f4.jar"), 500_000, daysAgo(1));

        CleanupOutcome outcome = service.runCleanup();

        PruneStats mavenStats = statsFor(outcome, mavenCache);
        assertThat(mavenStats.sizeDeletedFiles()).isEqualTo(3);
        assertThat(f1).doesNotExist();
        assertThat(f2).doesNotExist();
        assertThat(f3).doesNotExist();
        assertThat(f4).exists();
    }

    @Test
    void sizePhaseDoesNotRunWhenBelowHighWatermark() throws IOException {
        service.setMavenMaxSize(DataSize.ofMegabytes(100));
        touchFileWithAtime(mavenCache.resolve("a/small.jar"), 1024, daysAgo(5));

        CleanupOutcome outcome = service.runCleanup();

        PruneStats mavenStats = statsFor(outcome, mavenCache);
        assertThat(mavenStats.sizeDeletedFiles()).isZero();
        assertThat(mavenStats.ageDeletedFiles()).isZero();
    }

    @Test
    void hardCapDominatesEvenWhenAgeThresholdIsHigh() throws IOException {
        // Age threshold so high nothing is age-eligible — size cap must still bite.
        // High 500, low 400 (ratio 0.8). Total 800 → evict oldest 400-byte file → remaining 400 ≤ low, stop.
        service.setMaxAgeDays(10_000);
        service.setMavenMaxSize(DataSize.ofBytes(500));
        service.setLowWatermarkRatio(0.8);

        Path f1 = touchFileWithAtime(mavenCache.resolve("a/f1.jar"), 400, daysAgo(20));
        Path f2 = touchFileWithAtime(mavenCache.resolve("a/f2.jar"), 400, daysAgo(1));

        CleanupOutcome outcome = service.runCleanup();

        PruneStats mavenStats = statsFor(outcome, mavenCache);
        assertThat(mavenStats.ageDeletedFiles()).isZero();
        assertThat(mavenStats.sizeDeletedFiles()).isEqualTo(1);
        assertThat(f1).doesNotExist();
        assertThat(f2).exists();
    }

    @Test
    void emptyDirectoriesAreSweptAfterPrune() throws IOException {
        touchFileWithAtime(mavenCache.resolve("nested/dir/old.jar"), 100, daysAgo(60));

        CleanupOutcome outcome = service.runCleanup();

        PruneStats mavenStats = statsFor(outcome, mavenCache);
        assertThat(mavenStats.ageDeletedFiles()).isEqualTo(1);
        assertThat(mavenStats.emptyDirsRemoved()).isGreaterThanOrEqualTo(2);
        assertThat(mavenCache.resolve("nested/dir")).doesNotExist();
        assertThat(mavenCache.resolve("nested")).doesNotExist();
        assertThat(mavenCache).exists();
    }

    @Test
    void missingCachePathIsTreatedAsErrorAndDoesNotAbortOthers() throws IOException {
        Path missing = mavenCache.resolve("does-not-exist");
        when(buildAgentConfiguration.mavenCacheHostPath()).thenReturn(missing);
        touchFileWithAtime(gradleCache.resolve("g/old.jar"), 1024, daysAgo(60));

        CleanupOutcome outcome = service.runCleanup();

        // Both caches produce stats; Maven cache logs an error but does not blow up.
        assertThat(outcome.perCache()).hasSize(2);
        PruneStats missingStats = statsFor(outcome, missing);
        assertThat(missingStats.errors()).isEqualTo(1);
        PruneStats gradleStats = statsFor(outcome, gradleCache);
        assertThat(gradleStats.ageDeletedFiles()).isEqualTo(1);
        verify(sharedQueueProcessingService, times(1)).resumeFromMaintenance();
    }

    @Test
    void resumeRunsEvenWhenPruneThrows() throws IOException {
        // Configure a nonexistent path so the prune logs and continues; the resume in finally must still happen.
        when(buildAgentConfiguration.mavenCacheHostPath()).thenReturn(mavenCache.resolve("does-not-exist"));
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);

        service.runCleanup();

        verify(sharedQueueProcessingService, times(1)).pauseForMaintenance();
        verify(sharedQueueProcessingService, times(1)).resumeFromMaintenance();
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private static Instant daysAgo(int days) {
        return Instant.now().minus(Duration.ofDays(days));
    }

    private static Path touchFileWithAtime(Path file, int sizeBytes, Instant atime) throws IOException {
        Files.createDirectories(file.getParent());
        byte[] data = new byte[sizeBytes];
        Files.write(file, data);
        Files.setAttribute(file, "basic:lastAccessTime", FileTime.from(atime));
        return file;
    }

    private static PruneStats statsFor(CleanupOutcome outcome, Path root) {
        return outcome.perCache().stream().filter(s -> s.root().equals(root)).findFirst()
                .orElseThrow(() -> new AssertionError("no PruneStats found for " + root + " in outcome " + outcome.perCache()));
    }

    // Suppress unused-imports warnings — Mockito imports may not all be in use depending on test additions.
    @SuppressWarnings("unused")
    private static void ignored() {
        any();
        anyBoolean();
    }
}
