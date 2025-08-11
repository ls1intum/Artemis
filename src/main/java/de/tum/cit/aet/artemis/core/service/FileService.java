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

import jakarta.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.util.FileUtil;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class FileService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final Map<Path, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

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
     * Evict the cache for the given path
     *
     * @param path the path for the file to evict from cache
     */
    @CacheEvict(value = "files", key = "#path")
    public void evictCacheForPath(Path path) {
        log.info("Invalidate files cache for {}", path);
        // Intentionally blank
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
