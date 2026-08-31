package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.cache.BlobCacheEvictionService;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class FileService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    /**
     * Resolved lazily because {@code BlobCacheEvictionService} is itself declared {@code @Lazy}, so Spring injects a
     * deferred proxy. That matters here: this service is wired very early, and eagerly creating the eviction service
     * would pull up the cache manager, and with it the distributed data provider, ahead of the deferred initialisation
     * the rest of the startup sequence relies on.
     */
    @Nullable
    private final BlobCacheEvictionService blobCacheEvictionService;

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
     * For the JPA entities that construct this service directly to reach its path helpers. Such an instance cannot
     * broadcast cache evictions, and {@link #evictCacheForPath(Path)} reports that loudly rather than silently skipping
     * the eviction.
     */
    public FileService() {
        this.blobCacheEvictionService = null;
        this.tempFileUtilService = null;
    }

    @Autowired
    public FileService(BlobCacheEvictionService blobCacheEvictionService, TempFileUtilService tempFileUtilService) {
        this.blobCacheEvictionService = blobCacheEvictionService;
        this.tempFileUtilService = tempFileUtilService;
    }

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    @Override
    public void destroy() {
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
        if (blobCacheEvictionService == null) {
            log.error("Cannot evict the files cache for {}: this FileService was constructed directly instead of being injected", path);
            return;
        }
        blobCacheEvictionService.evictEverywhere("files", path.toString());
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
        futures.removeIf(ScheduledFuture::isDone);
        futures.add(executor.schedule(() -> {
            try {
                deletion.run();
            }
            catch (IOException e) {
                if (Files.exists(path)) {
                    log.error("Deleting {} did not work", path, e);
                }
                else {
                    log.debug("Deleting {} did not complete because it was removed concurrently", path, e);
                }
            }
        }, delayInMinutes, TimeUnit.MINUTES));
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
