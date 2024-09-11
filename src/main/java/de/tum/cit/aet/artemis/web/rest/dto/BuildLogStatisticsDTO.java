package de.tum.cit.aet.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildLogStatisticsDTO(Long buildCount, Double agentSetupDuration, Double testDuration, Double scaDuration, Double totalJobDuration,
        Double dependenciesDownloadedCount) {
}
