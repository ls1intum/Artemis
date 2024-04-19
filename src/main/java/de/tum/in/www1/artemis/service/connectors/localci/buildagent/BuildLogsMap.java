package de.tum.in.www1.artemis.service.connectors.localci.buildagent;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_BUILDAGENT;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.BuildLogEntry;

@Profile(PROFILE_BUILDAGENT)
@Component
public class BuildLogsMap {

    private ConcurrentMap<String, List<BuildLogEntry>> buildLogsMap = new ConcurrentHashMap<>();

    public List<BuildLogEntry> getBuildLogs(String buildLogId) {
        return buildLogsMap.get(buildLogId);
    }

    public void addSingleBuildLog(String buildLogId, BuildLogEntry buildLog) {
        buildLogsMap.computeIfAbsent(buildLogId, k -> new ArrayList<>()).add(buildLog);
    }

    public void removeBuildLogs(String buildLogId) {
        buildLogsMap.remove(buildLogId);
    }
}
