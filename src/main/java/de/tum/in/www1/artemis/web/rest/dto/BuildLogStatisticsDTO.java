package de.tum.in.www1.artemis.web.rest.dto;

public record BuildLogStatisticsDTO(Long buildCount, Double agentSetupDuration, Double testDuration, Double scaDuration, Double totalJobDuration,
        Double dependenciesDownloadedCount) {

}
