package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
import de.tum.cit.aet.artemis.core.util.FileUtil;

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

    private final Map<Path, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    /**
     * For the JPA entities that construct this service directly to reach its path helpers. Such an instance cannot
     * broadcast cache evictions, and {@link #evictCacheForPath(Path)} reports that loudly rather than silently skipping
     * the eviction.
     */
    public FileService() {
        this.blobCacheEvictionService = null;
    }

    @Autowired
    public FileService(BlobCacheEvictionService blobCacheEvictionService) {
        this.blobCacheEvictionService = blobCacheEvictionService;
    }

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    @Override
    public void destroy() {
        futures.values().forEach(future -> future.cancel(true));
        futures.clear();
    }

    /**
     * Get the file for the given path as a byte[]
     *
     * @param path the path for the file to load
     * @return file contents as a byte[], or null, if the file doesn't exist
     * @throws IOException if the file can't be accessed.
     */
    @Cacheable(value = "files", unless = "#result == null")
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
        blobCacheEvictionService.evictEverywhere("files", path);
    }

    /**
     * Schedule the deletion of the given nullsafe path with a given delay
     *
     * @param path           The path that should be deleted
     * @param delayInMinutes The delay in minutes after which the path should be deleted
     */
    public void schedulePathForDeletion(@Nullable Path path, long delayInMinutes) {
        if (path == null) {
            return;
        }
        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                if (Files.exists(path)) {
                    log.info("Delete file {}", path);
                    Files.delete(path);
                }
                else {
                    log.error("Deleting the file {} did not work because it does not exist", path);
                }

                futures.remove(path);
            }
            catch (IOException e) {
                log.error("Deleting the file {} did not work", path);
                log.error("Exception during deletion of file", e);
            }
        }, delayInMinutes, TimeUnit.MINUTES);

        futures.put(path, future);
    }

    /**
     * Schedule the recursive deletion of the given nullsafe directory with a given delay.
     *
     * @param path           The path to the directory that should be deleted
     * @param delayInMinutes The delay in minutes after which the path should be deleted
     */
    public void scheduleDirectoryPathForRecursiveDeletion(@Nullable Path path, long delayInMinutes) {
        if (path == null) {
            return;
        }
        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                if (Files.exists(path) && Files.isDirectory(path)) {
                    log.debug("Delete directory {}", path);
                    FileUtils.deleteDirectory(path.toFile());
                }
                futures.remove(path);
            }
            catch (IOException e) {
                log.error("Deleting the directory {} did not work", path);
                log.error("Exception during deletion of directory", e);
            }
        }, delayInMinutes, TimeUnit.MINUTES);

        futures.put(path, future);
    }

    /**
     * create a unique path by appending a folder named with the current milliseconds (e.g. 1609579674868) of the system and schedules it for deletion.
     * See {@link FileUtil#getUniqueSubfolderPath(Path)} for more information.
     *
     * @param path                 the original path, e.g. /opt/artemis/repos-download
     * @param deleteDelayInMinutes the delay in minutes after which the path should be deleted
     * @return the unique path, e.g. /opt/artemis/repos-download/1609579674868
     */
    public Path getTemporaryUniqueSubfolderPath(Path path, long deleteDelayInMinutes) {
        var temporaryPath = FileUtil.getUniqueSubfolderPath(path);
        scheduleDirectoryPathForRecursiveDeletion(temporaryPath, deleteDelayInMinutes);
        return temporaryPath;
    }

    /**
     * Create a unique path by appending a folder named with the current milliseconds (e.g. 1609579674868) of the system but does not create the folder.
     * This is used when cloning the programming exercises into a new temporary directory because if we already create the directory, the git clone does not work anymore.
     * The directory will be scheduled for deletion.
     *
     * @param path                 the original path, e.g. /opt/artemis/repos-download
     * @param deleteDelayInMinutes the delay in minutes after which the path should be deleted
     * @return the unique path, e.g. /opt/artemis/repos-download/1609579674868
     */
    public Path getTemporaryUniquePathWithoutPathCreation(Path path, long deleteDelayInMinutes) {
        var temporaryPath = path.resolve(String.valueOf(System.currentTimeMillis()));
        scheduleDirectoryPathForRecursiveDeletion(temporaryPath, deleteDelayInMinutes);
        return temporaryPath;
    }

}
