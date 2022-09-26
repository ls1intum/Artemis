package de.tum.in.www1.artemis.web.rest.dto;

public class BuildLogStatisticsDTO {

    private Long buildCount;

    private Double agentSetupDuration;

    private Double testDuration;

    private Double scaDuration;

    private Double totalJobDuration;

    private Double dependenciesDownloadedCount;

    public BuildLogStatisticsDTO(Long buildCount, Double agentSetupDuration, Double testDuration, Double scaDuration, Double totalJobDuration, Double dependenciesDownloadedCount) {
        this.buildCount = buildCount;
        this.agentSetupDuration = agentSetupDuration;
        this.testDuration = testDuration;
        this.scaDuration = scaDuration;
        this.totalJobDuration = totalJobDuration;
        this.dependenciesDownloadedCount = dependenciesDownloadedCount;
    }

    public Long getBuildCount() {
        return buildCount;
    }

    public void setBuildCount(Long buildCount) {
        this.buildCount = buildCount;
    }

    public Double getAgentSetupDuration() {
        return agentSetupDuration;
    }

    public void setAgentSetupDuration(Double agentSetupDuration) {
        this.agentSetupDuration = agentSetupDuration;
    }

    public Double getTestDuration() {
        return testDuration;
    }

    public void setTestDuration(Double testDuration) {
        this.testDuration = testDuration;
    }

    public Double getScaDuration() {
        return scaDuration;
    }

    public void setScaDuration(Double scaDuration) {
        this.scaDuration = scaDuration;
    }

    public Double getTotalJobDuration() {
        return totalJobDuration;
    }

    public void setTotalJobDuration(Double totalJobDuration) {
        this.totalJobDuration = totalJobDuration;
    }

    public Double getDependenciesDownloadedCount() {
        return dependenciesDownloadedCount;
    }

    public void setDependenciesDownloadedCount(Double dependenciesDownloadedCount) {
        this.dependenciesDownloadedCount = dependenciesDownloadedCount;
    }
}
