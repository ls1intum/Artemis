package de.tum.in.www1.artemis.service.connectors.localci.buildagent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.BuildLogEntry;

@Component
public class BuildLogsMap {

    private ConcurrentMap<String, List<BuildLogEntry>> buildLogsMap = new ConcurrentHashMap<>();

    public List<BuildLogEntry> getBuildLogs(String buildPlanId) {
        return buildLogsMap.get(buildPlanId);
    }

    public void addSingleBuildLog(String buildPlanId, BuildLogEntry buildLog) {
        buildLogsMap.computeIfAbsent(buildPlanId, k -> new ArrayList<>()).add(buildLog);
    }

    public void removeBuildLogs(String buildPlanId) {
        buildLogsMap.remove(buildPlanId);
    }
}
