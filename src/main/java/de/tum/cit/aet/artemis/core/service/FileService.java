package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOExceptionList;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.cache.PerNodeCacheEvictionService;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class FileService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    /**
     * Resolved lazily because {@code PerNodeCacheEvictionService} is itself declared {@code @Lazy}, so Spring injects a
     * deferred proxy. That matters here: this service is wired very early, and eagerly creating the eviction service
     * would pull up the cache manager, and with it the distributed data provider, ahead of the deferred initialisation
     * the rest of the startup sequence relies on.
     */
    @Nullable
    private final PerNodeCacheEvictionService perNodeCacheEvictionService;

    /**
     * Only needed by {@link #createTemporaryDirectory(Path, String, long)}, so it is absent on the directly constructed
     * instances below, which never create temporary directories.
     */
    @Nullable
    private final TempFileUtilService tempFileUtilService;

    /**
     * The pending deletions, kept only so {@link #destroy()} can cancel them. Deliberately not keyed by path: two
     * cleanups may legitimately target the same path, and a map would drop one of the futures and leave it running past
     * shutdown.
     */
    private final Set<ScheduledFuture<?>> futures = ConcurrentHashMap.newKeySet();

    /**
     * The cleanups that are still tracked for cancellation. Visible for testing: whether a schedule is tracked at all is
     * exactly what regressed when this was a map keyed by path, and it cannot be observed from the outside otherwise.
     *
     * @return a snapshot of the currently tracked deletions
     */
    Set<ScheduledFuture<?>> pendingDeletions() {
        return Set.copyOf(futures);
    }

    /**
     * For the JPA entities that construct this service directly to reach its path helpers. Such an instance cannot
     * broadcast cache evictions, and {@link #evictCacheForPath(Path)} reports that loudly rather than silently skipping
     * the eviction.
     */
    public FileService() {
        this.perNodeCacheEvictionService = null;
        this.tempFileUtilService = null;
    }

    @Autowired
    public FileService(PerNodeCacheEvictionService perNodeCacheEvictionService, TempFileUtilService tempFileUtilService) {
        this.perNodeCacheEvictionService = perNodeCacheEvictionService;
        this.tempFileUtilService = tempFileUtilService;
    }

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    /**
     * Cancels the pending cleanups and shuts the scheduler down, so that a cleanup scheduled while the context is closing
     * is rejected rather than added to a pool nobody cancels afterwards, and so that the pool's non-daemon threads do not
     * outlive the bean.
     */
    @Override
    public void destroy() {
        executor.shutdownNow();
        futures.forEach(future -> future.cancel(true));
        futures.clear();
    }

    /**
     * Get the file for the given path as a byte[]
     *
     * @param path the path for the file to load
     * @return file contents as a byte[], or null, if the file doesn't exist
     * @throws IOException if the file can't be accessed.
     */
    // Keyed by the path string rather than the Path object: the eviction broadcast has to carry the key across nodes, and
    // Path instances are neither portable between JVMs nor comparable across filesystem providers.
    @Cacheable(value = "files", key = "#path.toString()", unless = "#result == null")
    public byte[] getFileForPath(Path path) throws IOException {
        if (Files.exists(path)) {
            return Files.readAllBytes(path);
        }
        return null;
    }

    /**
     * Evict the cache for the given path on every node.
     *
     * <p>
     * The {@code files} cache is per-node (see {@link de.tum.cit.aet.artemis.core.config.cache.BlobCacheConfiguration}),
     * so a local {@code @CacheEvict} would leave the other nodes serving the previous file content. The broadcast below is
     * what makes the eviction cluster-wide.
     *
     * @param path the path for the file to evict from cache
     */
    public void evictCacheForPath(Path path) {
        log.debug("Invalidate files cache for {}", path);
        if (perNodeCacheEvictionService == null) {
            log.error("Cannot evict the files cache for {}: this FileService was constructed directly instead of being injected", path);
            return;
        }
        perNodeCacheEvictionService.evictEverywhere("files", path.toString());
    }

    /**
     * Schedule the deletion of the given nullsafe path with a given delay
     *
     * @param path           The path that should be deleted
     * @param delayInMinutes The delay in minutes after which the path should be deleted
     */
    public void schedulePathForDeletion(@Nullable Path path, long delayInMinutes) {
        scheduleDeletion(path, delayInMinutes, () -> {
            if (Files.exists(path)) {
                log.info("Delete file {}", path);
                Files.delete(path);
            }
            else {
                log.debug("Not deleting the file {} because it no longer exists", path);
            }
        });
    }

    /**
     * Schedule the recursive deletion of the given nullsafe directory with a given delay.
     *
     * @param path           The path to the directory that should be deleted
     * @param delayInMinutes The delay in minutes after which the path should be deleted
     */
    public void scheduleDirectoryPathForRecursiveDeletion(@Nullable Path path, long delayInMinutes) {
        scheduleDeletion(path, delayInMinutes, () -> {
            if (Files.exists(path) && Files.isDirectory(path)) {
                log.debug("Delete directory {}", path);
                FileUtils.deleteDirectory(path.toFile());
            }
        });
    }

    /**
     * Runs the given best-effort deletion after the delay and keeps its future around until {@link #destroy()} or the
     * next scheduling call prunes it.
     *
     * <p>
     * A deletion that fails because the target has meanwhile disappeared is not reported as an error: these cleanups run
     * minutes after the work that requested them, so another cleanup, an operator or a container restart may legitimately
     * have removed the tree first. Anything else is still logged as an error.
     */
    private void scheduleDeletion(@Nullable Path path, long delayInMinutes, IoRunnable deletion) {
        if (path == null) {
            return;
        }
        // Pruned here rather than by the task itself: a zero-minute delay can run the task before its future is even
        // assigned, so a self-removing task has nothing to remove itself from.
        futures.removeIf(ScheduledFuture::isDone);
        try {
            futures.add(executor.schedule(() -> {
                try {
                    deletion.run();
                }
                catch (IOException e) {
                    if (isCausedByMissingFile(e) || !Files.exists(path)) {
                        log.debug("Deleting {} did not complete because it was removed concurrently", path, e);
                    }
                    else {
                        log.error("Deleting {} did not work", path, e);
                    }
                }
            }, delayInMinutes, TimeUnit.MINUTES));
        }
        catch (RejectedExecutionException e) {
            // The context is shutting down. Leaving the path behind is the correct outcome: it is a temporary path that
            // the next start cleans up, and failing the caller's request over it would be worse.
            log.debug("Not scheduling the deletion of {} because the scheduler is shutting down", path);
        }
    }

    /**
     * Whether the failure is only that something the deletion wanted to remove had already been removed.
     *
     * <p>
     * Checking whether the root still exists is not enough on its own: a concurrent cleanup that is halfway through the
     * tree leaves the root in place, and the walk then fails on a file that vanished underneath it. That is exactly the
     * shape of the failure in issue #13575, and it is reported as
     * {@code IOExceptionList -> IOIndexedException -> FileNotFoundException -> NoSuchFileException}, so the whole cause
     * chain has to be inspected.
     *
     * <p>
     * A walk that collected several failures counts as benign only if every one of them is a missing file. Otherwise a
     * single permission or I/O error would be demoted to debug just because it happened to be reported alongside a file
     * another cleanup had already removed.
     */
    // Package-private so the test can pin the exact exception shapes without having to provoke the race.
    static boolean isCausedByMissingFile(Throwable failure) {
        if (failure instanceof IOExceptionList exceptionList) {
            var collectedFailures = exceptionList.getCauseList();
            return !collectedFailures.isEmpty() && collectedFailures.stream().allMatch(FileService::isCausedByMissingFile);
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof NoSuchFileException || cause instanceof FileNotFoundException) {
                return true;
            }
            if (cause instanceof IOExceptionList) {
                return isCausedByMissingFile(cause);
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface IoRunnable {

        void run() throws IOException;
    }

    /**
     * Creates a temporary directory below the given parent and schedules it for recursive deletion.
     *
     * <p>
     * The directory name is generated atomically by the file system, so concurrent callers can never be handed the same
     * path. Naming it after the wall clock, as this method's predecessors did, gave every export running on the same
     * millisecond one shared directory and one cleanup task each, which then raced each other (issue #13575).
     *
     * <p>
     * Callers that clone repositories should create one directory per operation and place each repository in a
     * deterministic subdirectory of it, rather than requesting a directory per repository: that keeps the cleanup to a
     * single task and removes the whole tree even when the export fails halfway through.
     *
     * @param parent               the directory to create the temporary directory in, e.g. /opt/artemis/repos-download
     * @param prefix               a prefix for the directory name, so leftovers can be traced back to their origin
     * @param deleteDelayInMinutes the delay in minutes after which the directory should be deleted
     * @return the newly created directory
     * @throws IOException if the directory could not be created
     */
    public Path createTemporaryDirectory(Path parent, String prefix, long deleteDelayInMinutes) throws IOException {
        if (tempFileUtilService == null) {
            throw new IllegalStateException("Cannot create a temporary directory: this FileService was constructed directly instead of being injected");
        }
        Path temporaryDirectory = tempFileUtilService.createTempDirectory(parent, prefix);
        scheduleDirectoryPathForRecursiveDeletion(temporaryDirectory, deleteDelayInMinutes);
        return temporaryDirectory;
    }

}
