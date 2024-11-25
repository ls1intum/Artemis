package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;

@Profile(PROFILE_BUILDAGENT)
@Component
public class BuildLogsMap {

    @Value("${artemis.continuous-integration.build-logs.max-lines-per-job:10000}")
    private int maxLogLinesPerBuildJob;

    @Value("${artemis.continuous-integration.build-logs.max-chars-per-line:1024}")
    private int maxCharsPerLine;

    private final ConcurrentMap<String, List<BuildLogEntry>> buildLogsMap = new ConcurrentHashMap<>();

    public List<BuildLogEntry> getBuildLogs(String buildLogId) {
        return buildLogsMap.get(buildLogId);
    }

    public void appendBuildLogEntry(String buildLogId, String message) {
        appendBuildLogEntry(buildLogId, new BuildLogEntry(ZonedDateTime.now(), message + "\n"));
    }

    public void appendBuildLogEntry(String buildLogId, BuildLogEntry buildLog) {
        buildLogsMap.computeIfAbsent(buildLogId, k -> new ArrayList<>()).add(buildLog);
    }

    public void removeBuildLogs(String buildLogId) {
        buildLogsMap.remove(buildLogId);
    }

    public List<BuildLogEntry> getAndTruncateBuildLogs(String buildLogId) {
        List<BuildLogEntry> buildLogs = buildLogsMap.get(buildLogId);

        if (buildLogs == null) {
            return null;
        }

        // Truncate the build logs to maxLogLinesPerBuildJob
        if (buildLogs.size() > maxLogLinesPerBuildJob) {
            List<BuildLogEntry> truncatedBuildLogs = new ArrayList<>(buildLogs.subList(0, maxLogLinesPerBuildJob));
            truncatedBuildLogs.add(new BuildLogEntry(ZonedDateTime.now(), "Truncated build logs...\n"));
            buildLogs = truncatedBuildLogs;
        }

        // Truncate each line to maxCharsPerLine
        for (int i = 0; i < buildLogs.size(); i++) {
            BuildLogEntry buildLog = buildLogs.get(i);
            String log = buildLog.getLog();
            if (log.length() > maxCharsPerLine) {
                String truncatedLog = log.substring(0, maxCharsPerLine) + "\n";
                buildLogs.set(i, new BuildLogEntry(buildLog.getTime(), truncatedLog));
            }
        }

        return buildLogs;
    }
}
