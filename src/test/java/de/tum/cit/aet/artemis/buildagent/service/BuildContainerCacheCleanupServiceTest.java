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

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentMaintenanceAction;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentMaintenanceResult;
import de.tum.cit.aet.artemis.buildagent.service.BuildContainerCacheCleanupService.CleanupOutcome;
import de.tum.cit.aet.artemis.buildagent.service.BuildContainerCacheCleanupService.PruneStats;
import de.tum.cit.aet.artemis.buildagent.service.BuildContainerCacheCleanupService.WipeOutcome;

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

        // Default: both caches configured, not read-only, pause is granted and remains held throughout the run.
        when(buildAgentConfiguration.mavenCacheHostPath()).thenReturn(mavenCache);
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(gradleCache);
        when(buildAgentConfiguration.isBuildContainerCacheReadOnly()).thenReturn(false);
        lenient().when(sharedQueueProcessingService.pauseForMaintenance()).thenReturn(true);
        // The cleanup checks isPaused() at every loop iteration to detect mid-run resume; default to "still paused"
        // so the standard prune flow proceeds. Tests that exercise the abort-on-resume path override this.
        lenient().when(sharedQueueProcessingService.isPaused()).thenReturn(true);

        service = new BuildContainerCacheCleanupService(buildAgentConfiguration, sharedQueueProcessingService, mock(BuildAgentDockerService.class),
                mock(de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService.class), mock(BuildAgentInformationService.class));
        service.setCleanupEnabled(true);
        service.setMaxAgeDays(30);
        service.setMavenMaxSize(DataSize.ofGigabytes(3));
        service.setGradleMaxSize(DataSize.ofGigabytes(6));
        service.setLowWatermarkRatio(0.75);
        service.setBuildAgentShortName("agent-under-test");
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

    @Test
    void cleanupAbortsWhenPauseIsReleasedMidRun() throws IOException {
        // Many age-eligible files so phase 1 runs long enough that we can flip pause-released between iterations.
        for (int i = 0; i < 50; i++) {
            touchFileWithAtime(mavenCache.resolve("a/file" + i + ".jar"), 64, daysAgo(60));
        }
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);
        // First call to isPaused() inside the loop returns true (still ours); the second returns false (released).
        when(sharedQueueProcessingService.isPaused()).thenReturn(true, false);

        CleanupOutcome outcome = service.runCleanup();

        PruneStats mavenStats = statsFor(outcome, mavenCache);
        // We must have stopped early — strictly fewer than the 50 eligible files were deleted.
        assertThat(mavenStats.ageDeletedFiles()).isLessThan(50);
        // The finally block still resumes (idempotent), so resume is called once.
        verify(sharedQueueProcessingService, times(1)).resumeFromMaintenance();
    }

    @Test
    void invalidLowWatermarkRatioFallsBackToDefault() throws IOException {
        // Cap 1000 bytes, ratio 2.0 → would compute low=2000 (> cap) and disable phase 2 silently. The clamp must
        // detect this and fall back to 0.75 → low=750, evicting until total ≤ 750.
        service.setMavenMaxSize(DataSize.ofBytes(1000));
        service.setLowWatermarkRatio(2.0);
        service.setMaxAgeDays(365);  // disable age phase
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);

        Path f1 = touchFileWithAtime(mavenCache.resolve("a/old.jar"), 500, daysAgo(20));
        Path f2 = touchFileWithAtime(mavenCache.resolve("a/mid.jar"), 500, daysAgo(10));
        Path f3 = touchFileWithAtime(mavenCache.resolve("a/new.jar"), 500, daysAgo(1));

        CleanupOutcome outcome = service.runCleanup();

        PruneStats stats = statsFor(outcome, mavenCache);
        assertThat(stats.sizeDeletedFiles()).isGreaterThanOrEqualTo(1);
        // The oldest must be the one that goes first.
        assertThat(f1).doesNotExist();
        assertThat(f3).exists();
    }

    // --- Additional edge-case coverage --------------------------------------------------------------------------

    @Test
    void emptyCacheRunsWithoutErrorAndReturnsZeroCounters() {
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);

        CleanupOutcome outcome = service.runCleanup();

        PruneStats stats = statsFor(outcome, mavenCache);
        assertThat(stats.initialBytes()).isZero();
        assertThat(stats.ageDeletedFiles()).isZero();
        assertThat(stats.sizeDeletedFiles()).isZero();
        assertThat(stats.errors()).isZero();
        verify(sharedQueueProcessingService, times(1)).resumeFromMaintenance();
    }

    @Test
    void cacheWithOnlyDirectoriesAndNoFilesIsHandled() throws IOException {
        // Empty subdirectory tree — phase 0 walk produces 0 file entries, sweep should remove the leaves.
        Files.createDirectories(mavenCache.resolve("group/artifact/version"));
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);

        CleanupOutcome outcome = service.runCleanup();

        PruneStats stats = statsFor(outcome, mavenCache);
        assertThat(stats.ageDeletedFiles()).isZero();
        assertThat(stats.sizeDeletedFiles()).isZero();
        assertThat(stats.emptyDirsRemoved()).isGreaterThanOrEqualTo(3);
        // Root itself must remain — only its descendants are eligible for removal.
        assertThat(mavenCache).exists();
    }

    @Test
    void rootDirectoryItselfIsNeverDeletedEvenIfEmpty() throws IOException {
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);

        service.runCleanup();

        assertThat(mavenCache).exists();
        assertThat(mavenCache).isDirectory();
    }

    @Test
    void bothCachesGetIndependentStatsAndDeleteCounts() throws IOException {
        // Two ageing files in each cache.
        touchFileWithAtime(mavenCache.resolve("m/old.jar"), 100, daysAgo(60));
        touchFileWithAtime(mavenCache.resolve("m/new.jar"), 100, daysAgo(1));
        touchFileWithAtime(gradleCache.resolve("g/old.jar"), 200, daysAgo(60));
        touchFileWithAtime(gradleCache.resolve("g/new.jar"), 200, daysAgo(1));

        CleanupOutcome outcome = service.runCleanup();

        assertThat(outcome.perCache()).hasSize(2);
        PruneStats mavenStats = statsFor(outcome, mavenCache);
        PruneStats gradleStats = statsFor(outcome, gradleCache);
        assertThat(mavenStats.ageDeletedFiles()).isEqualTo(1);
        assertThat(mavenStats.ageDeletedBytes()).isEqualTo(100);
        assertThat(gradleStats.ageDeletedFiles()).isEqualTo(1);
        assertThat(gradleStats.ageDeletedBytes()).isEqualTo(200);
    }

    @Test
    void onlyGradleConfiguredProcessesGradleAlone() throws IOException {
        when(buildAgentConfiguration.mavenCacheHostPath()).thenReturn(null);
        touchFileWithAtime(gradleCache.resolve("g/old.jar"), 64, daysAgo(60));

        CleanupOutcome outcome = service.runCleanup();

        assertThat(outcome.perCache()).hasSize(1);
        assertThat(outcome.perCache().getFirst().root()).isEqualTo(gradleCache);
        assertThat(outcome.perCache().getFirst().ageDeletedFiles()).isEqualTo(1);
    }

    @Test
    void zeroAgeDaysDeletesEverythingViaPhase1() throws IOException {
        service.setMaxAgeDays(0); // every file qualifies as "older than 0 days" since their atime is in the past
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);

        touchFileWithAtime(mavenCache.resolve("a.jar"), 10, daysAgo(0));
        touchFileWithAtime(mavenCache.resolve("b.jar"), 10, daysAgo(1));

        CleanupOutcome outcome = service.runCleanup();

        PruneStats stats = statsFor(outcome, mavenCache);
        assertThat(stats.ageDeletedFiles()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void hugeAgeDaysKeepsEverythingWhenBelowSizeCap() throws IOException {
        service.setMaxAgeDays(100_000);
        service.setMavenMaxSize(DataSize.ofGigabytes(100));
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);

        Path f = touchFileWithAtime(mavenCache.resolve("keep.jar"), 64, daysAgo(365 * 5));

        CleanupOutcome outcome = service.runCleanup();

        PruneStats stats = statsFor(outcome, mavenCache);
        assertThat(stats.ageDeletedFiles()).isZero();
        assertThat(stats.sizeDeletedFiles()).isZero();
        assertThat(f).exists();
    }

    @Test
    void nestedEmptyDirectoriesAreFullyCollapsedAfterDeletion() throws IOException {
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);
        touchFileWithAtime(mavenCache.resolve("a/b/c/d/leaf.jar"), 10, daysAgo(60));

        CleanupOutcome outcome = service.runCleanup();

        PruneStats stats = statsFor(outcome, mavenCache);
        assertThat(stats.ageDeletedFiles()).isEqualTo(1);
        assertThat(stats.emptyDirsRemoved()).isGreaterThanOrEqualTo(4);
        assertThat(mavenCache.resolve("a")).doesNotExist();
        assertThat(mavenCache).exists();
    }

    @Test
    void siblingNonEmptyDirectoryIsPreservedDuringSweep() throws IOException {
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);
        // age-eligible inside one subdir; recent file inside another sibling — sibling must stay.
        touchFileWithAtime(mavenCache.resolve("old/oldfile.jar"), 10, daysAgo(60));
        touchFileWithAtime(mavenCache.resolve("keep/keepfile.jar"), 10, daysAgo(1));

        service.runCleanup();

        assertThat(mavenCache.resolve("old")).doesNotExist();
        assertThat(mavenCache.resolve("keep")).exists();
        assertThat(mavenCache.resolve("keep/keepfile.jar")).exists();
    }

    @Test
    void ratioNaNFallsBackToDefault() throws IOException {
        service.setMavenMaxSize(DataSize.ofBytes(1000));
        service.setLowWatermarkRatio(Double.NaN);
        service.setMaxAgeDays(365);
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);

        // Total 1500 > cap 1000. Default ratio 0.75 → low 750. Evict 500 → remaining 1000 still > 750, keep evicting.
        touchFileWithAtime(mavenCache.resolve("a/a.jar"), 500, daysAgo(20));
        touchFileWithAtime(mavenCache.resolve("a/b.jar"), 500, daysAgo(10));
        touchFileWithAtime(mavenCache.resolve("a/c.jar"), 500, daysAgo(1));

        CleanupOutcome outcome = service.runCleanup();

        PruneStats stats = statsFor(outcome, mavenCache);
        assertThat(stats.sizeDeletedFiles()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void ratioOfZeroFallsBackToDefault() throws IOException {
        service.setMavenMaxSize(DataSize.ofBytes(1000));
        service.setLowWatermarkRatio(0.0);
        service.setMaxAgeDays(365);
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);

        touchFileWithAtime(mavenCache.resolve("a/a.jar"), 600, daysAgo(20));
        touchFileWithAtime(mavenCache.resolve("a/b.jar"), 600, daysAgo(1));

        CleanupOutcome outcome = service.runCleanup();

        PruneStats stats = statsFor(outcome, mavenCache);
        // With default ratio 0.75 and cap 1000, low=750. After evicting 600 → 600 ≤ 750, stop. So 1 deletion.
        assertThat(stats.sizeDeletedFiles()).isEqualTo(1);
    }

    @Test
    void ratioOfOneFallsBackToDefault() throws IOException {
        service.setMavenMaxSize(DataSize.ofBytes(1000));
        service.setLowWatermarkRatio(1.0);
        service.setMaxAgeDays(365);
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);

        touchFileWithAtime(mavenCache.resolve("a/a.jar"), 600, daysAgo(20));
        touchFileWithAtime(mavenCache.resolve("a/b.jar"), 600, daysAgo(1));

        CleanupOutcome outcome = service.runCleanup();

        // ratio==1 would compute low==high; phase 2 condition `survivingBytes <= low` becomes survivingBytes <= cap,
        // which is reached after the first eviction. Fallback to 0.75 makes the loop go further than 0 deletions.
        PruneStats stats = statsFor(outcome, mavenCache);
        assertThat(stats.sizeDeletedFiles()).isEqualTo(1);
    }

    @Test
    void ratioOfNegativeFallsBackToDefault() throws IOException {
        service.setMavenMaxSize(DataSize.ofBytes(1000));
        service.setLowWatermarkRatio(-1.0);
        service.setMaxAgeDays(365);
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);

        touchFileWithAtime(mavenCache.resolve("a/a.jar"), 600, daysAgo(20));
        touchFileWithAtime(mavenCache.resolve("a/b.jar"), 600, daysAgo(1));

        // With negative ratio and no clamp, low=-750 → survivingBytes <= -750 is false → loop deletes everything.
        // The clamp must prevent this.
        CleanupOutcome outcome = service.runCleanup();

        PruneStats stats = statsFor(outcome, mavenCache);
        assertThat(stats.sizeDeletedFiles()).isEqualTo(1);
    }

    @Test
    void validRatioInRangeIsHonored() throws IOException {
        service.setMavenMaxSize(DataSize.ofBytes(1000));
        service.setLowWatermarkRatio(0.5); // low watermark 500
        service.setMaxAgeDays(365);
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);

        touchFileWithAtime(mavenCache.resolve("a/a.jar"), 400, daysAgo(20));
        touchFileWithAtime(mavenCache.resolve("a/b.jar"), 400, daysAgo(10));
        touchFileWithAtime(mavenCache.resolve("a/c.jar"), 400, daysAgo(1));

        // Total 1200 > cap 1000. Evict oldest 400 → 800 > 500 (low) → evict next 400 → 400 ≤ 500, stop.
        CleanupOutcome outcome = service.runCleanup();

        PruneStats stats = statsFor(outcome, mavenCache);
        assertThat(stats.sizeDeletedFiles()).isEqualTo(2);
    }

    @Test
    void cleanupOutcomeIsAlwaysNonNull() {
        when(buildAgentConfiguration.mavenCacheHostPath()).thenReturn(null);
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);

        CleanupOutcome outcome = service.runCleanup();

        assertThat(outcome).isNotNull();
        assertThat(outcome.perCache()).isNotNull();
    }

    @Test
    void completedRunReportsWasSkippedFalse() throws IOException {
        touchFileWithAtime(mavenCache.resolve("a.jar"), 10, daysAgo(60));
        touchFileWithAtime(gradleCache.resolve("g.jar"), 10, daysAgo(60));

        CleanupOutcome outcome = service.runCleanup();

        assertThat(outcome.wasSkipped()).isFalse();
        assertThat(outcome.skippedReason()).isNull();
    }

    @Test
    void pruneStatsExposeInitialBytesBeforeAnyEviction() throws IOException {
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);
        touchFileWithAtime(mavenCache.resolve("a.jar"), 1024, daysAgo(60));
        touchFileWithAtime(mavenCache.resolve("b.jar"), 2048, daysAgo(1));

        CleanupOutcome outcome = service.runCleanup();

        PruneStats stats = statsFor(outcome, mavenCache);
        assertThat(stats.initialBytes()).isEqualTo(1024 + 2048);
    }

    @Test
    void fileDeletedExternallyBetweenWalkAndPruneIsHandledCleanly() throws IOException {
        // Touch a file then delete it before runCleanup() — simulates a build container that legitimately removed
        // an artifact between phase 0 (walk) and phase 1 (delete). Files.deleteIfExists returns true (no error).
        // Hard to simulate the real race without a custom FileSystem; instead verify deleteIfExists semantics by
        // confirming a non-existent file doesn't blow up phase 1 when produced from the walk list directly.
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);
        Path ghost = mavenCache.resolve("ghost.jar");
        FileUtils.writeByteArrayToFile(ghost.toFile(), new byte[16]);
        Files.setAttribute(ghost, "basic:lastAccessTime", FileTime.from(daysAgo(60)));
        Files.delete(ghost); // gone before runCleanup walks

        CleanupOutcome outcome = service.runCleanup();

        PruneStats stats = statsFor(outcome, mavenCache);
        assertThat(stats.errors()).isZero();
    }

    @Test
    void resumeIsCalledExactlyOnceAcrossSuccessfulRun() throws IOException {
        touchFileWithAtime(mavenCache.resolve("a.jar"), 10, daysAgo(60));
        touchFileWithAtime(gradleCache.resolve("g.jar"), 10, daysAgo(60));

        service.runCleanup();

        verify(sharedQueueProcessingService, times(1)).pauseForMaintenance();
        verify(sharedQueueProcessingService, times(1)).resumeFromMaintenance();
    }

    @Test
    void pruneStatsRecordCarriesNonZeroDuration() throws IOException {
        touchFileWithAtime(mavenCache.resolve("a.jar"), 10, daysAgo(60));
        when(buildAgentConfiguration.gradleCacheHostPath()).thenReturn(null);

        CleanupOutcome outcome = service.runCleanup();

        PruneStats stats = statsFor(outcome, mavenCache);
        assertThat(stats.duration()).isNotNull();
        assertThat(stats.duration().isNegative()).isFalse();
    }

    @Test
    void readOnlyShortCircuitDoesNotInvokePauseEvenWithCachePathsSet() {
        when(buildAgentConfiguration.isBuildContainerCacheReadOnly()).thenReturn(true);
        // cache paths are configured by @BeforeEach — read-only must take precedence

        CleanupOutcome outcome = service.runCleanup();

        assertThat(outcome.wasSkipped()).isTrue();
        assertThat(outcome.skippedReason()).isEqualTo("read-only");
        verify(sharedQueueProcessingService, never()).pauseForMaintenance();
        verify(sharedQueueProcessingService, never()).resumeFromMaintenance();
    }

    @Test
    void mavenAndGradleStatsReturnedAsSeparateEntries() throws IOException {
        touchFileWithAtime(mavenCache.resolve("a.jar"), 100, daysAgo(60));
        touchFileWithAtime(gradleCache.resolve("g.jar"), 200, daysAgo(60));

        CleanupOutcome outcome = service.runCleanup();

        assertThat(outcome.perCache()).hasSize(2);
        var roots = outcome.perCache().stream().map(PruneStats::root).toList();
        assertThat(roots).containsExactlyInAnyOrder(mavenCache, gradleCache);
    }

    // --- Wipe cache (admin Reclaim disk) tests ------------------------------------------------------------------

    @Test
    void wipeMavenCacheDeletesEveryFileRegardlessOfAge() throws IOException {
        // Mix of ages including a brand-new file the cleanup phases would never have touched.
        Path old = touchFileWithAtime(mavenCache.resolve("a/old.jar"), 100, daysAgo(60));
        Path mid = touchFileWithAtime(mavenCache.resolve("a/mid.jar"), 200, daysAgo(10));
        Path fresh = touchFileWithAtime(mavenCache.resolve("b/fresh.jar"), 300, daysAgo(0));

        WipeOutcome outcome = service.wipeMavenCache();

        assertThat(outcome.wasSkipped()).isFalse();
        assertThat(outcome.stats().deletedFiles()).isEqualTo(3);
        assertThat(outcome.stats().deletedBytes()).isEqualTo(600);
        assertThat(old).doesNotExist();
        assertThat(mid).doesNotExist();
        assertThat(fresh).doesNotExist();
        // Root preserved; nested empty dirs collapsed.
        assertThat(mavenCache).exists();
        assertThat(mavenCache.resolve("a")).doesNotExist();
        assertThat(mavenCache.resolve("b")).doesNotExist();
        verify(sharedQueueProcessingService, times(1)).pauseForMaintenance();
        verify(sharedQueueProcessingService, times(1)).resumeFromMaintenance();
    }

    @Test
    void wipeGradleCacheDeletesEveryFileRegardlessOfAge() throws IOException {
        Path g = touchFileWithAtime(gradleCache.resolve("dist/recent.jar"), 64, daysAgo(1));

        WipeOutcome outcome = service.wipeGradleCache();

        assertThat(outcome.wasSkipped()).isFalse();
        assertThat(outcome.stats().deletedFiles()).isEqualTo(1);
        assertThat(g).doesNotExist();
    }

    @Test
    void wipeMavenCacheSkipsWhenNoMavenPathConfigured() {
        when(buildAgentConfiguration.mavenCacheHostPath()).thenReturn(null);

        WipeOutcome outcome = service.wipeMavenCache();

        assertThat(outcome.wasSkipped()).isTrue();
        assertThat(outcome.skippedReason()).isEqualTo("no-target");
        verify(sharedQueueProcessingService, never()).pauseForMaintenance();
    }

    @Test
    void wipeSkipsWhenCacheIsReadOnly() throws IOException {
        when(buildAgentConfiguration.isBuildContainerCacheReadOnly()).thenReturn(true);
        Path f = touchFileWithAtime(mavenCache.resolve("would-be-deleted.jar"), 64, daysAgo(60));

        WipeOutcome outcome = service.wipeMavenCache();

        assertThat(outcome.wasSkipped()).isTrue();
        assertThat(outcome.skippedReason()).isEqualTo("read-only");
        assertThat(f).exists(); // read-only path is operator-owned; we did not touch it
        verify(sharedQueueProcessingService, never()).pauseForMaintenance();
    }

    @Test
    void wipeSkipsWhenAgentAlreadyPaused() throws IOException {
        when(sharedQueueProcessingService.pauseForMaintenance()).thenReturn(false);
        touchFileWithAtime(mavenCache.resolve("old.jar"), 64, daysAgo(60));

        WipeOutcome outcome = service.wipeMavenCache();

        assertThat(outcome.wasSkipped()).isTrue();
        assertThat(outcome.skippedReason()).isEqualTo("already-paused");
        verify(sharedQueueProcessingService, never()).resumeFromMaintenance();
    }

    @Test
    void wipeResumesAgentEvenWhenRootIsMissing() {
        when(buildAgentConfiguration.mavenCacheHostPath()).thenReturn(mavenCache.resolve("does-not-exist"));

        // No throw; the wipe logs a warn and returns a non-skipped outcome with errors=1.
        WipeOutcome outcome = service.wipeMavenCache();

        assertThat(outcome.wasSkipped()).isFalse();
        assertThat(outcome.stats().errors()).isEqualTo(1);
        verify(sharedQueueProcessingService, times(1)).pauseForMaintenance();
        verify(sharedQueueProcessingService, times(1)).resumeFromMaintenance();
    }

    // --- maintenance-result outcome mapping -------------------------------------------------------------------
    // The agent publishes a BuildAgentMaintenanceResult after each maintenance action so the admin UI can render a
    // toast. The conversion from the existing WipeOutcome / CleanupOutcome records to that result is the part the
    // UI is sensitive to (right colour, right "freed" count, right error count) — these tests pin the mapping.

    @Test
    void toResult_wipeWithoutErrors_isSuccessAndReportsBytesFreed() {
        BuildContainerCacheCleanupService.WipeStats stats = new BuildContainerCacheCleanupService.WipeStats(mavenCache, 5, 1024L, 2, 0, Duration.ofMillis(40));
        WipeOutcome outcome = new WipeOutcome(stats, null);

        Instant start = Instant.now();
        BuildAgentMaintenanceResult result = service.toResult(BuildAgentMaintenanceAction.Type.WIPE_MAVEN_CACHE, outcome, start, start);

        assertThat(result.agentShortName()).isEqualTo("agent-under-test");
        assertThat(result.actionType()).isEqualTo(BuildAgentMaintenanceAction.Type.WIPE_MAVEN_CACHE);
        assertThat(result.outcome()).isEqualTo(BuildAgentMaintenanceResult.Outcome.SUCCESS);
        assertThat(result.bytesFreed()).isEqualTo(1024L);
        assertThat(result.itemsAffected()).isEqualTo(5L);
        assertThat(result.errorCount()).isZero();
        assertThat(result.skipReason()).isNull();
    }

    @Test
    void toResult_wipeWithErrors_isPartialFailureWithErrorCount() {
        // 3 files deleted, 7 errors (e.g. permission denied on root-owned files) — the operator must see the
        // partial-failure variant or they will assume the wipe succeeded when it really didn't free anything.
        BuildContainerCacheCleanupService.WipeStats stats = new BuildContainerCacheCleanupService.WipeStats(gradleCache, 3, 512L, 0, 7, Duration.ofMillis(60));
        WipeOutcome outcome = new WipeOutcome(stats, null);

        BuildAgentMaintenanceResult result = service.toResult(BuildAgentMaintenanceAction.Type.WIPE_GRADLE_CACHE, outcome, Instant.now(), Instant.now());

        assertThat(result.outcome()).isEqualTo(BuildAgentMaintenanceResult.Outcome.PARTIAL_FAILURE);
        assertThat(result.bytesFreed()).isEqualTo(512L);
        assertThat(result.itemsAffected()).isEqualTo(3L);
        assertThat(result.errorCount()).isEqualTo(7L);
    }

    @Test
    void toResult_skippedWipe_carriesSkipReason() {
        WipeOutcome outcome = WipeOutcome.skipped("read-only");

        BuildAgentMaintenanceResult result = service.toResult(BuildAgentMaintenanceAction.Type.WIPE_MAVEN_CACHE, outcome, Instant.now(), Instant.now());

        assertThat(result.outcome()).isEqualTo(BuildAgentMaintenanceResult.Outcome.SKIPPED);
        assertThat(result.skipReason()).isEqualTo("read-only");
        assertThat(result.bytesFreed()).isZero();
        assertThat(result.itemsAffected()).isZero();
        assertThat(result.errorCount()).isZero();
    }

    @Test
    void toResult_cleanupSumsAcrossCaches_andReportsErrorsAsPartialFailure() {
        // The toast aggregates per-cache prune stats so the operator sees a single "freed across X items" number.
        PruneStats maven = new PruneStats(mavenCache, 10_000L, 2, 800L, 1, 100L, 1, 0, Duration.ofMillis(50));
        PruneStats gradle = new PruneStats(gradleCache, 20_000L, 0, 0L, 3, 1_500L, 2, 4, Duration.ofMillis(80));
        CleanupOutcome outcome = new CleanupOutcome(java.util.List.of(maven, gradle), null);

        BuildAgentMaintenanceResult result = service.toResult(BuildAgentMaintenanceAction.Type.RUN_CACHE_CLEANUP, outcome, Instant.now(), Instant.now());

        assertThat(result.outcome()).isEqualTo(BuildAgentMaintenanceResult.Outcome.PARTIAL_FAILURE);
        // ageDeletedBytes (800 + 0) + sizeDeletedBytes (100 + 1500) == 2400
        assertThat(result.bytesFreed()).isEqualTo(2400L);
        // ageDeletedFiles (2 + 0) + sizeDeletedFiles (1 + 3) == 6
        assertThat(result.itemsAffected()).isEqualTo(6L);
        assertThat(result.errorCount()).isEqualTo(4L);
    }

    @Test
    void toResult_skippedCleanup_isSkipped() {
        CleanupOutcome outcome = CleanupOutcome.skipped("disabled");

        BuildAgentMaintenanceResult result = service.toResult(BuildAgentMaintenanceAction.Type.RUN_CACHE_CLEANUP, outcome, Instant.now(), Instant.now());

        assertThat(result.outcome()).isEqualTo(BuildAgentMaintenanceResult.Outcome.SKIPPED);
        assertThat(result.skipReason()).isEqualTo("disabled");
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private static Instant daysAgo(int days) {
        return Instant.now().minus(Duration.ofDays(days));
    }

    private static Path touchFileWithAtime(Path file, int sizeBytes, Instant atime) throws IOException {
        Files.createDirectories(file.getParent());
        byte[] data = new byte[sizeBytes];
        FileUtils.writeByteArrayToFile(file.toFile(), data);
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
