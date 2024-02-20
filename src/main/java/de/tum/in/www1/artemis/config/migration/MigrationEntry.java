package de.tum.in.www1.artemis.config.migration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MigrationEntry {

    private static final Logger log = LoggerFactory.getLogger(MigrationEntry.class);

    public abstract void execute();

    /**
     * @return Author of the entry. Either full name or GitHub name.
     */
    public abstract String author();

    /**
     * Format YYYYMMDD_HHmmss
     *
     * @return Current time in given format
     */
    public abstract String date();

    protected static void shutdown(ExecutorService executorService, int timeoutInHours, String errorMessage) {
        // Wait for all threads to finish
        executorService.shutdown();

        try {
            boolean finished = executorService.awaitTermination(timeoutInHours, TimeUnit.HOURS);
            if (!finished) {
                log.error(errorMessage);
                if (executorService.awaitTermination(1, TimeUnit.MINUTES)) {
                    log.error("Failed to cancel all migration threads. Some threads are still running.");
                }
                throw new RuntimeException(errorMessage);
            }
        }
        catch (InterruptedException e) {
            log.error(errorMessage);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
