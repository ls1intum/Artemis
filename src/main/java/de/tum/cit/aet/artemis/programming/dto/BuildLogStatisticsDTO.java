package de.tum.cit.aet.artemis.programming.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildLogStatisticsDTO(Long buildCount, Double agentSetupDuration, Double testDuration, Double scaDuration, Double totalJobDuration,
        Double dependenciesDownloadedCount) {

    @NotNull
    @Override
    public Long buildCount() {
        return buildCount != null ? buildCount : 0;
    }

    @NotNull
    @Override
    public Double agentSetupDuration() {
        return agentSetupDuration != null ? agentSetupDuration : 0.0;
    }

    @NotNull
    @Override
    public Double testDuration() {
        return testDuration != null ? testDuration : 0.0;
    }

    @NotNull
    @Override
    public Double scaDuration() {
        return scaDuration != null ? scaDuration : 0.0;
    }

    @NotNull
    @Override
    public Double totalJobDuration() {
        return totalJobDuration != null ? totalJobDuration : 0.0;
    }

    @NotNull
    @Override
    public Double dependenciesDownloadedCount() {
        return dependenciesDownloadedCount != null ? dependenciesDownloadedCount : 0.0;
    }
}
