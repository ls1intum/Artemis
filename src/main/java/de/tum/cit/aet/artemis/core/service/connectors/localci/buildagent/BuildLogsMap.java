package de.tum.cit.aet.artemis.core.service.connectors.localci.buildagent;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.programming.domain.BuildLogEntry;

@Profile(PROFILE_BUILDAGENT)
@Component
public class BuildLogsMap {

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
}
