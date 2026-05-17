package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;

/**
 * Drains the build agent, then prunes the persistent Maven and Gradle dependency caches that live on the host and are
 * bind-mounted into every build container (see {@link BuildAgentConfiguration#buildContainerCacheBinds()}).
 * <p>
 * <h2>Why JVM-driven instead of a host crontab</h2>
 * The host crontab approach we previously used (a {@code find -atime +30 -delete} via Ansible) could not coordinate
 * with the build agent's lifecycle: if a prune fired while a build was reading a soon-to-be-deleted artifact, the
 * build could observe an {@code ENOENT}, re-download from Maven Central, and finish slightly slower. The race was
 * narrow (atime is bumped whenever a file is read under the default {@code relatime} mount), but it could happen,
 * and the cron's trailing {@code || true} silently swallowed any other failure. Driving cleanup from inside the
 * agent JVM lets us:
 * <ul>
 * <li>{@link BuildAgentConfiguration#buildContainerCacheBinds() pause-and-quiesce} so no build is reading the cache
 * while we modify it,</li>
 * <li>respect the existing read-only cache mode (in {@code read-only=true} the operator owns cache contents and we
 * must never prune),</li>
 * <li>read the cache paths from a single source of truth (the Spring config) rather than templating them into both
 * application config and a host crontab.</li>
 * </ul>
 *
 * <h2>Two-phase eviction</h2>
 * Each configured cache is processed independently. The walk is performed once and the file metadata is materialised
 * into a list; two filters then run against that list:
 * <ol>
 * <li><b>Age-based prune (soft target).</b> Delete every regular file whose access time is older than
 * {@code cleanup-max-age-days} (default 30 d). This is the day-to-day mechanism that keeps the cache lean.</li>
 * <li><b>Size-based eviction (hard cap).</b> If the surviving total size still exceeds the per-cache high watermark
 * (default 3 GB Maven, 6 GB Gradle), delete files in ascending order of access time (oldest used first) until the
 * remaining total drops below {@code high * size-low-watermark-ratio} (default 0.75 → 2.25 GB / 4.5 GB).</li>
 * </ol>
 * After both phases, empty directories are swept post-order so the layout matches what Maven/Gradle would create
 * on the next miss. The hard cap dominates: if an operator sets the age threshold very high or disables age-pruning
 * via configuration, the size cap still fires and bounds total disk use.
 *
 * <h2>Pause semantics</h2>
 * The cleanup task obtains an exclusive maintenance pause through {@link SharedQueueProcessingService#pauseForMaintenance()},
 * which returns {@code true} only when this call actually transitioned the agent from running to paused. If the
 * return is {@code false}, the agent was already paused (by an administrator, the failure-backoff mechanism, or a
 * previous cleanup cycle that overlapped); the task logs and aborts <em>without resuming</em>, leaving ownership
 * of the pause with whoever already held it.
 *
 * <h2>Wall-clock safety cap</h2>
 * The whole cleanup invocation is bounded by {@link #MAX_CLEANUP_DURATION}. If walking and pruning the caches
 * exceeds this duration (pathological FS state, NFS hang, etc.), the in-flight phase finishes its current file,
 * remaining files are skipped, and the agent is resumed. A subsequent invocation re-runs the cleanup from scratch.
 *
 * <h2>Per-agent staggering</h2>
 * The cron expression is exposed as a Spring property so Ansible can template a per-host offset (e.g. 3 minutes
 * apart across agents in the same group). Even if multiple agents drain simultaneously, the cluster degrades to
 * (N − live − draining)/N capacity rather than failing builds; queued jobs wait briefly. The default schedule
 * (05:00 UTC) targets a low-traffic window.
 *
 * <h2>Read-only caches</h2>
 * When {@link BuildAgentConfiguration#isBuildContainerCacheReadOnly()} returns {@code true}, this service short-
 * circuits with a log line and does not pause the agent. The cache contents are then the operator's responsibility
 * (typical pattern: warm the cache out-of-band, then bind it read-only into builds).
 */
@Service
@Profile(PROFILE_BUILDAGENT)
public class BuildContainerCacheCleanupService {

    private static final Logger log = LoggerFactory.getLogger(BuildContainerCacheCleanupService.class);

    /** Hard upper bound on a single cleanup invocation. */
    static final Duration MAX_CLEANUP_DURATION = Duration.ofMinutes(15);

    private final BuildAgentConfiguration buildAgentConfiguration;

    private final SharedQueueProcessingService sharedQueueProcessingService;

    @Value("${artemis.continuous-integration.build-container-cache.cleanup-enabled:true}")
    private boolean cleanupEnabled;

    @Value("${artemis.continuous-integration.build-container-cache.cleanup-max-age-days:30}")
    private int maxAgeDays;

    @Value("${artemis.continuous-integration.build-container-cache.maven-max-size:3GB}")
    private DataSize mavenMaxSize;

    @Value("${artemis.continuous-integration.build-container-cache.gradle-max-size:6GB}")
    private DataSize gradleMaxSize;

    @Value("${artemis.continuous-integration.build-container-cache.size-low-watermark-ratio:0.75}")
    private double lowWatermarkRatio;

    public BuildContainerCacheCleanupService(BuildAgentConfiguration buildAgentConfiguration, SharedQueueProcessingService sharedQueueProcessingService) {
        this.buildAgentConfiguration = buildAgentConfiguration;
        this.sharedQueueProcessingService = sharedQueueProcessingService;
    }

    /**
     * Scheduled entry point. Fires on the cron expression configured via
     * {@code artemis.continuous-integration.build-container-cache.cleanup-cron} (default {@code 0 0 5 * * *}).
     */
    @Scheduled(cron = "${artemis.continuous-integration.build-container-cache.cleanup-cron:0 0 5 * * *}")
    public void scheduledCleanup() {
        runCleanup();
    }

    /**
     * Performs one cleanup cycle: validate config, pause the agent, prune each configured cache, resume the agent.
     * Package-private so tests can drive it directly without depending on the Spring scheduler.
     *
     * @return a summary of what was done, primarily for tests and metrics. Never {@code null}.
     */
    CleanupOutcome runCleanup() {
        if (!cleanupEnabled) {
            log.debug("Build-container cache cleanup is disabled (cleanup-enabled=false); skipping.");
            return CleanupOutcome.skipped("disabled");
        }
        if (buildAgentConfiguration.isBuildContainerCacheReadOnly()) {
            log.info("Build-container cache is read-only; cleanup is the operator's responsibility, skipping.");
            return CleanupOutcome.skipped("read-only");
        }
        List<CacheTarget> targets = collectTargets();
        if (targets.isEmpty()) {
            log.debug("No build-container cache paths configured; skipping cleanup.");
            return CleanupOutcome.skipped("no-targets");
        }

        log.info("Pausing build agent for build-container cache cleanup (targets: {})", targets.stream().map(t -> t.root.toString()).toList());
        List<PruneStats> perCache = new ArrayList<>(targets.size());
        Optional<String> skipReason = pausedAndDo("cache cleanup", deadline -> {
            for (CacheTarget target : targets) {
                if (Instant.now().isAfter(deadline)) {
                    log.warn("Wall-clock cap of {} reached during cache cleanup before processing {}; skipping remaining caches until the next cycle.", MAX_CLEANUP_DURATION,
                            target.root);
                    break;
                }
                perCache.add(prune(target, deadline));
            }
        });
        if (skipReason.isPresent()) {
            return CleanupOutcome.skipped(skipReason.get());
        }
        return new CleanupOutcome(perCache, null);
    }

    /**
     * Wipes the Maven cache: pauses the agent, deletes every regular file under the configured cache root
     * regardless of age, sweeps empty directories, then resumes. Returns immediately if no Maven cache is
     * configured or the cache is mounted read-only.
     */
    public WipeOutcome wipeMavenCache() {
        return wipe(buildAgentConfiguration.mavenCacheHostPath(), "Maven cache wipe");
    }

    /**
     * Wipes the Gradle cache: same semantics as {@link #wipeMavenCache()} against the Gradle cache root.
     */
    public WipeOutcome wipeGradleCache() {
        return wipe(buildAgentConfiguration.gradleCacheHostPath(), "Gradle cache wipe");
    }

    private WipeOutcome wipe(Path root, String description) {
        if (root == null) {
            log.debug("{} requested but no cache path is configured; skipping.", description);
            return WipeOutcome.skipped("no-target");
        }
        if (buildAgentConfiguration.isBuildContainerCacheReadOnly()) {
            log.info("Build-container cache is read-only; {} is the operator's responsibility, skipping.", description);
            return WipeOutcome.skipped("read-only");
        }
        log.info("Pausing build agent for {} (target: {})", description, root);
        AtomicReference<WipeStats> statsRef = new AtomicReference<>();
        Optional<String> skipReason = pausedAndDo(description, deadline -> statsRef.set(wipeRoot(root, deadline)));
        if (skipReason.isPresent()) {
            return WipeOutcome.skipped(skipReason.get());
        }
        return new WipeOutcome(statsRef.get(), null);
    }

    /**
     * Pause/work/resume scaffolding shared by {@link #runCleanup()}, {@link #wipeMavenCache()},
     * {@link #wipeGradleCache()}. The {@code work} consumer receives the wall-clock deadline so each phase inside
     * can self-cap (the pause + 15 min budget is the contract; the existing per-loop {@code isCleanupAborted}
     * check inside both the prune and the wipe re-checks this deadline and the agent's paused state).
     *
     * @return {@code Optional.empty()} when this call owned the pause and ran the work to completion; the reason
     *         string otherwise (currently always {@code "already-paused"} — the only short-circuit at this level).
     */
    private Optional<String> pausedAndDo(String description, java.util.function.Consumer<Instant> work) {
        boolean ownsPause = sharedQueueProcessingService.pauseForMaintenance();
        if (!ownsPause) {
            log.warn("Build agent was already paused when {} tried to start; skipping to avoid resuming someone else's pause.", description);
            return Optional.of("already-paused");
        }
        Instant deadline = Instant.now().plus(MAX_CLEANUP_DURATION);
        try {
            work.accept(deadline);
            return Optional.empty();
        }
        finally {
            log.info("Resuming build agent after {}", description);
            sharedQueueProcessingService.resumeFromMaintenance();
        }
    }

    /**
     * Single-walk wipe of a cache root: deletes every regular file regardless of age, then sweeps empty
     * directories post-order. The caller has already acquired the maintenance pause.
     */
    WipeStats wipeRoot(Path root, Instant deadline) {
        Instant start = Instant.now();
        if (!Files.isDirectory(root)) {
            log.warn("Configured cache path {} does not exist or is not a directory; nothing to wipe.", root);
            return new WipeStats(root, 0, 0, 0, 1, Duration.between(start, Instant.now()));
        }
        long initialBytes = 0;
        int deletedFiles = 0;
        long deletedBytes = 0;
        int errors = 0;
        List<FileEntry> entries;
        try {
            entries = walk(root, deadline);
        }
        catch (IOException e) {
            log.warn("Walking cache {} failed; wipe will be skipped.", root, e);
            return new WipeStats(root, 0, 0, 0, 1, Duration.between(start, Instant.now()));
        }
        for (FileEntry entry : entries) {
            initialBytes += entry.size();
            if (isCleanupAborted(deadline, "wipe", root)) {
                break;
            }
            if (tryDelete(entry.path())) {
                deletedFiles++;
                deletedBytes += entry.size();
            }
            else {
                errors++;
            }
        }
        int emptyDirsRemoved = sweepEmptyDirectories(root, deadline);
        Duration duration = Duration.between(start, Instant.now());
        log.info("Cache wipe for {}: deleted={} files / {} bytes (of {} initial bytes); empty dirs removed={}; errors={}; took {}", root, deletedFiles, deletedBytes, initialBytes,
                emptyDirsRemoved, errors, duration);
        return new WipeStats(root, deletedFiles, deletedBytes, emptyDirsRemoved, errors, duration);
    }

    /**
     * Single-walk two-phase prune for one cache root.
     *
     * @param target   the cache to prune
     * @param deadline absolute wall-clock cutoff; the prune stops when reached (partial work is kept)
     * @return statistics about the prune (file/byte counts per phase, errors)
     */
    PruneStats prune(CacheTarget target, Instant deadline) {
        Instant start = Instant.now();
        Path root = target.root;
        if (!Files.isDirectory(root)) {
            log.warn("Configured cache path {} does not exist or is not a directory; skipping.", root);
            return summariseAndLog(root, 0, 0, 0, 0, 0, 0, 1, start);
        }

        // Phase 0: collect file metadata in a single walk. The walk is bounded by the wall-clock deadline so a
        // hung filesystem (NFS stall, etc.) cannot keep the agent paused indefinitely.
        List<FileEntry> entries = new ArrayList<>();
        int errors = 0;
        try {
            entries = walk(root, deadline);
        }
        catch (IOException e) {
            log.warn("Walking cache {} failed; the prune for this cache will be skipped.", root, e);
            errors++;
            return summariseAndLog(root, 0, 0, 0, 0, 0, 0, errors, start);
        }

        long initialBytes = entries.stream().mapToLong(FileEntry::size).sum();
        Instant ageCutoff = Instant.now().minus(Duration.ofDays(maxAgeDays));

        // Phase 1: age-based eviction. Remove every age-eligible entry from the in-memory list whether or not the
        // file delete succeeded — phase 2 must not retry an already-failed delete (would double-count errors and
        // be guaranteed to fail again for the same reason).
        long ageDeletedBytes = 0;
        int ageDeletedFiles = 0;
        var it = entries.iterator();
        while (it.hasNext()) {
            if (isCleanupAborted(deadline, "phase 1 (age)", root)) {
                break;
            }
            FileEntry entry = it.next();
            if (entry.atime.isBefore(ageCutoff)) {
                if (tryDelete(entry.path)) {
                    ageDeletedFiles++;
                    ageDeletedBytes += entry.size;
                }
                else {
                    errors++;
                }
                it.remove();
            }
        }

        // Phase 2: size-based eviction (LRU by atime among survivors).
        long survivingBytes = initialBytes - ageDeletedBytes;
        long high = target.maxSize.toBytes();
        long low = Math.round(high * effectiveLowWatermarkRatio());
        long sizeDeletedBytes = 0;
        int sizeDeletedFiles = 0;
        if (survivingBytes > high) {
            entries.sort(Comparator.comparing(FileEntry::atime));
            for (FileEntry entry : entries) {
                if (survivingBytes <= low || isCleanupAborted(deadline, "phase 2 (size)", root)) {
                    break;
                }
                if (tryDelete(entry.path)) {
                    sizeDeletedFiles++;
                    sizeDeletedBytes += entry.size;
                    survivingBytes -= entry.size;
                }
                else {
                    errors++;
                }
            }
        }

        // Sweep empty directories post-order. Walk afresh because the in-memory list does not track dirs.
        int emptyDirsRemoved = sweepEmptyDirectories(root, deadline);

        return summariseAndLog(root, initialBytes, ageDeletedFiles, ageDeletedBytes, sizeDeletedFiles, sizeDeletedBytes, emptyDirsRemoved, errors, start);
    }

    /**
     * Emits the canonical {@code Cache prune for <root>: ...} info line and builds the {@link PruneStats} record.
     * Centralised so every exit point — including the early returns on missing root and walk failure — produces the
     * grep marker that the admin runbook documents.
     */
    private PruneStats summariseAndLog(Path root, long initialBytes, int ageDeletedFiles, long ageDeletedBytes, int sizeDeletedFiles, long sizeDeletedBytes, int emptyDirsRemoved,
            int errors, Instant start) {
        Duration duration = Duration.between(start, Instant.now());
        log.info("Cache prune for {}: age-deleted={} files / {} bytes; size-deleted={} files / {} bytes; empty dirs removed={}; errors={}; took {}", root, ageDeletedFiles,
                ageDeletedBytes, sizeDeletedFiles, sizeDeletedBytes, emptyDirsRemoved, errors, duration);
        return new PruneStats(root, initialBytes, ageDeletedFiles, ageDeletedBytes, sizeDeletedFiles, sizeDeletedBytes, emptyDirsRemoved, errors, duration);
    }

    /**
     * Returns {@code true} when the in-flight prune must stop — either the wall-clock cap has been reached or the
     * agent is no longer paused (admin/topic resume, failure-backoff). Logs once at the abort point so the operator
     * can correlate the partial work in the {@code Cache prune for ...} summary line.
     */
    private boolean isCleanupAborted(Instant deadline, String phase, Path root) {
        if (Instant.now().isAfter(deadline)) {
            log.warn("Wall-clock cap reached during {} for {}; remaining files skipped until the next cycle.", phase, root);
            return true;
        }
        if (!sharedQueueProcessingService.isPaused()) {
            log.warn("Pause was released during {} for {} (admin or topic resume); aborting prune to avoid racing with a live build.", phase, root);
            return true;
        }
        return false;
    }

    /**
     * Walks the cache tree and returns one {@link FileEntry} per regular file. Symlinks, sockets, FIFOs, and other
     * non-regular files are ignored (the Maven / Gradle cache structure does not contain them under normal use).
     * <p>
     * The walk itself is bounded by the wall-clock {@code deadline} and by the agent staying paused — checked every
     * {@value #WALK_DEADLINE_CHECK_INTERVAL} files. Without this, a hung filesystem (NFS stall, autofs backoff, …)
     * could keep the agent paused indefinitely while {@code Files.walkFileTree} blocked inside the kernel.
     */
    private List<FileEntry> walk(Path root, Instant deadline) throws IOException {
        List<FileEntry> result = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {

            int counter;

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if ((++counter & (WALK_DEADLINE_CHECK_INTERVAL - 1)) == 0 && isCleanupAborted(deadline, "phase 0 (walk)", root)) {
                    return FileVisitResult.TERMINATE;
                }
                if (attrs.isRegularFile()) {
                    FileTime atime = attrs.lastAccessTime();
                    result.add(new FileEntry(file, attrs.size(), atime.toInstant()));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                log.warn("Could not stat {} during cache walk; skipping.", file, exc);
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }

    /** Files between deadline checks during the walk. Power of two so we can mask instead of mod. */
    private static final int WALK_DEADLINE_CHECK_INTERVAL = 1024;

    /**
     * Post-order sweep of empty directories below {@code root} (the root itself is kept). Mirrors
     * {@code find <root> -mindepth 1 -type d -empty -delete} but skips on wall-clock deadline.
     */
    private int sweepEmptyDirectories(Path root, Instant deadline) {
        int[] removed = { 0 };
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    if (isCleanupAborted(deadline, "empty-dir sweep", root)) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (dir.equals(root)) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        Files.delete(dir);
                        removed[0]++;
                    }
                    catch (DirectoryNotEmptyException ignored) {
                        // expected: directory still has children
                    }
                    catch (IOException e) {
                        log.debug("Could not remove empty directory {}: {}", dir, e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e) {
            log.warn("Empty-directory sweep for {} failed", root, e);
        }
        return removed[0];
    }

    /**
     * @return {@code true} if the file was deleted (or already absent), {@code false} on I/O error
     */
    private boolean tryDelete(Path file) {
        try {
            return Files.deleteIfExists(file);
        }
        catch (IOException e) {
            log.warn("Failed to delete cache file {}: {}", file, e.getMessage());
            return false;
        }
    }

    /**
     * Returns {@link #lowWatermarkRatio} clamped to {@code (0.0, 1.0)}. Misconfigurations outside that range would
     * either disable phase-2 eviction entirely (ratio ≥ 1.0 → low ≥ high, condition {@code survivingBytes ≤ low}
     * is met immediately) or evict to zero / negative bytes (ratio ≤ 0.0). Either is silently wrong; we clamp and
     * warn so the operator notices in the log without crashing the agent at boot.
     */
    private double effectiveLowWatermarkRatio() {
        if (Double.isNaN(lowWatermarkRatio) || lowWatermarkRatio <= 0.0 || lowWatermarkRatio >= 1.0) {
            log.warn("size-low-watermark-ratio is {}, which is outside the open interval (0, 1); falling back to 0.75. Fix the configuration to silence this warning.",
                    lowWatermarkRatio);
            return 0.75;
        }
        return lowWatermarkRatio;
    }

    private List<CacheTarget> collectTargets() {
        List<CacheTarget> targets = new ArrayList<>(2);
        Path maven = buildAgentConfiguration.mavenCacheHostPath();
        if (maven != null) {
            targets.add(new CacheTarget(maven, mavenMaxSize));
        }
        Path gradle = buildAgentConfiguration.gradleCacheHostPath();
        if (gradle != null) {
            targets.add(new CacheTarget(gradle, gradleMaxSize));
        }
        return targets;
    }

    // --- Test hooks: package-private setters so tests can drive the service without a Spring context. ---

    void setCleanupEnabled(boolean cleanupEnabled) {
        this.cleanupEnabled = cleanupEnabled;
    }

    void setMaxAgeDays(int maxAgeDays) {
        this.maxAgeDays = maxAgeDays;
    }

    void setMavenMaxSize(DataSize mavenMaxSize) {
        this.mavenMaxSize = mavenMaxSize;
    }

    void setGradleMaxSize(DataSize gradleMaxSize) {
        this.gradleMaxSize = gradleMaxSize;
    }

    void setLowWatermarkRatio(double lowWatermarkRatio) {
        this.lowWatermarkRatio = lowWatermarkRatio;
    }

    /** One cache to prune (root path + size cap). */
    record CacheTarget(Path root, DataSize maxSize) {
    }

    /** One file's metadata captured during the walk. */
    record FileEntry(Path path, long size, Instant atime) {
    }

    /** Per-cache prune statistics. */
    public record PruneStats(Path root, long initialBytes, int ageDeletedFiles, long ageDeletedBytes, int sizeDeletedFiles, long sizeDeletedBytes, int emptyDirsRemoved, int errors,
            Duration duration) {
    }

    /** Outcome of one {@link #runCleanup()} invocation. */
    public record CleanupOutcome(List<PruneStats> perCache, String skippedReason) {

        static CleanupOutcome skipped(String reason) {
            return new CleanupOutcome(List.of(), reason);
        }

        public boolean wasSkipped() {
            return skippedReason != null;
        }
    }

    /** Per-wipe statistics. {@code initialBytes} is the size of the cache before any file was deleted. */
    public record WipeStats(Path root, int deletedFiles, long deletedBytes, int emptyDirsRemoved, int errors, Duration duration) {
    }

    /** Outcome of one {@link #wipeMavenCache()} / {@link #wipeGradleCache()} invocation. */
    public record WipeOutcome(WipeStats stats, String skippedReason) {

        static WipeOutcome skipped(String reason) {
            return new WipeOutcome(null, reason);
        }

        public boolean wasSkipped() {
            return skippedReason != null;
        }
    }
}
