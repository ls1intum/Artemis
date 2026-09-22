package de.tum.cit.aet.artemis.admin.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import de.tum.cit.aet.artemis.core.config.RecentLogsAppender;
import de.tum.cit.aet.artemis.core.dto.IngestionLogEntryDTO;
import de.tum.cit.aet.artemis.core.dto.vm.LoggerVM;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.iris.api.IrisRecentLogsApi;

/**
 * Controller for view and managing Log Level at runtime.
 */
@Profile(PROFILE_CORE)
@EnforceAdmin
@Lazy
@FeatureUsage("monitoring/server-logs")
@RestController
@SuppressWarnings("deprecation")
@RequestMapping("api/admin/")
public class AdminLogResource {

    /**
     * Default number of records returned per service when the caller names no limit.
     */
    private static final int DEFAULT_LOG_LIMIT = 500;

    private final Optional<IrisRecentLogsApi> irisRecentLogsApi;

    public AdminLogResource(Optional<IrisRecentLogsApi> irisRecentLogsApi) {
        this.irisRecentLogsApi = irisRecentLogsApi;
    }

    /**
     * GET logs/ingestion : the recent ingestion log records of both services, newest first.
     * <p>
     * Artemis and Iris each keep a small in-memory buffer of their ingestion records; this merges them into one
     * time-ordered list so the dashboard shows a single story rather than asking a reader to correlate two. Both
     * services log only to stdout, so when the log collector is unavailable this is the only way to see why an
     * ingestion run behaved as it did without shell access to either host.
     * <p>
     * Iris being unreachable yields only the Artemis half rather than an error: half the story is worth more than
     * a failed page, and the dashboard says which sources answered.
     *
     * @param level return only records at exactly this level, or null for every level
     * @param limit how many records to take from each service before merging
     * @return the merged records, newest first
     */
    @GetMapping("logs/ingestion")
    public ResponseEntity<List<IngestionLogEntryDTO>> getIngestionLogs(@RequestParam(required = false) @Nullable String level,
            @RequestParam(defaultValue = "" + DEFAULT_LOG_LIMIT) int limit) {
        List<IngestionLogEntryDTO> merged = new ArrayList<>();

        for (RecentLogsAppender.RecentLogEvent event : RecentLogsAppender.snapshot()) {
            if (level != null && !level.equalsIgnoreCase(event.level())) {
                continue;
            }
            merged.add(new IngestionLogEntryDTO(IngestionLogEntryDTO.SOURCE_ARTEMIS, Instant.ofEpochMilli(event.timestamp()), event.level(), event.loggerName(), event.message(),
                    event.stackTrace()));
        }

        irisRecentLogsApi.ifPresent(api -> api.getRecentLogs(limit, level).forEach(entry -> merged.add(new IngestionLogEntryDTO(IngestionLogEntryDTO.SOURCE_IRIS,
                Instant.ofEpochMilli((long) entry.timestamp()), entry.level(), entry.logger(), entry.message(), entry.stackTrace()))));

        merged.sort(Comparator.comparing(IngestionLogEntryDTO::occurredAt).reversed());
        return ResponseEntity.ok(merged.size() > limit ? merged.subList(0, limit) : merged);
    }

    /**
     * GET logs -- Gets the current log levels.
     *
     * @return A list of all loggers with their log level
     */
    @GetMapping("logs")
    public ResponseEntity<List<LoggerVM>> getList() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        return ResponseEntity.ok(context.getLoggerList().stream().map(LoggerVM::new).toList());
    }

    /**
     * PUT logs -- Changes the log level of a logger.
     *
     * @param jsonLogger The logger with the new log level
     * @return The updated logger
     */
    @PutMapping("logs")
    public ResponseEntity<LoggerVM> changeLevel(@RequestBody LoggerVM jsonLogger) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(jsonLogger.getName());
        logger.setLevel(Level.valueOf(jsonLogger.getLevel()));
        return ResponseEntity.ok(new LoggerVM(logger));
    }
}
