package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;

@Profile(PROFILE_BUILDAGENT)
@Component
@Lazy(false)
public class BuildLogsMap {

    @Value("${artemis.continuous-integration.build-logs.max-lines-per-job:10000}")
    private int maxLogLinesPerBuildJob;

    @Value("${artemis.continuous-integration.build-logs.max-chars-per-line:1024}")
    private int maxCharsPerLine;

    // buildJobId --> List of build logs
    private final ConcurrentMap<String, List<BuildLogDTO>> buildLogsMap = new ConcurrentHashMap<>();

    /**
     * Appends a new build log entry to the build logs for the specified build job ID.
     *
     * @param buildJobId the ID of the build job to append a log message to
     * @param message    the message to append to the build log
     */
    public void appendBuildLogEntry(String buildJobId, String message) {
        appendBuildLogEntry(buildJobId, new BuildLogDTO(ZonedDateTime.now(), message + "\n"));
    }

    /**
     * Appends a new build log entry to the build logs for the specified build job ID.
     * Only the first maxCharsPerLine characters of the log message will be appended. Longer characters will be truncated to avoid memory issues.
     * Only the first maxLogLinesPerBuildJob log entries will be stored. Newer logs will be ignored to avoid memory issues
     *
     * @param buildJobId the ID of the build job to append a log message to
     * @param buildLog   the build log entry to append to the build log
     */
    public void appendBuildLogEntry(String buildJobId, BuildLogDTO buildLog) {
        List<BuildLogDTO> buildLogs = buildLogsMap.computeIfAbsent(buildJobId, k -> new ArrayList<>());
        if (buildLogs.size() < maxLogLinesPerBuildJob) {
            if (buildLog.log() != null && buildLog.log().length() > maxCharsPerLine) {
                buildLog = new BuildLogDTO(buildLog.time(), buildLog.log().substring(0, maxCharsPerLine) + "\n");
            }
            buildLogs.add(buildLog);
        }
    }

    public void removeBuildLogs(String buildJobId) {
        buildLogsMap.remove(buildJobId);
    }

    /**
     * Retrieves and truncates the build logs for the specified build job ID. Does not modify the original build logs.
     *
     * @param buildJobId the ID of the build job to retrieve and truncate
     * @return a list of truncated build log entries, or null if no logs are found for the specified ID
     */
    public List<BuildLogDTO> getAndTruncateBuildLogs(String buildJobId) {
        List<BuildLogDTO> buildLogs = buildLogsMap.get(buildJobId);

        if (buildLogs == null) {
            return null;
        }

        // Truncate the build logs to maxLogLinesPerBuildJob
        if (buildLogs.size() > maxLogLinesPerBuildJob) {
            List<BuildLogDTO> truncatedBuildLogs = new ArrayList<>(buildLogs.subList(0, maxLogLinesPerBuildJob));
            truncatedBuildLogs.add(new BuildLogDTO(ZonedDateTime.now(), "Truncated build logs...\n"));
            buildLogs = truncatedBuildLogs;
        }

        return buildLogs;
    }
}
