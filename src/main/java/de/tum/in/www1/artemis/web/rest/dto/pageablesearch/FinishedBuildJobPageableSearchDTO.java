package de.tum.in.www1.artemis.web.rest.dto.pageablesearch;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.enumeration.BuildStatus;

public class FinishedBuildJobPageableSearchDTO extends SearchTermPageableSearchDTO<String> {

    private BuildStatus buildStatus;

    private String buildAgentAddress;

    private ZonedDateTime startDate;

    private ZonedDateTime endDate;

    private int buildDurationLower;

    private int buildDurationUpper;

    public BuildStatus getBuildStatus() {
        return buildStatus;
    }

    public void setBuildStatus(BuildStatus buildStatus) {
        this.buildStatus = buildStatus;
    }

    public String getBuildAgentAddress() {
        return buildAgentAddress;
    }

    public void setBuildAgentAddress(String buildAgentAddress) {
        this.buildAgentAddress = buildAgentAddress;
    }

    public ZonedDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    public ZonedDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(ZonedDateTime endDate) {
        this.endDate = endDate;
    }

    public int getBuildDurationLower() {
        return buildDurationLower;
    }

    public void setBuildDurationLower(int buildDurationLower) {
        this.buildDurationLower = buildDurationLower;
    }

    public int getBuildDurationUpper() {
        return buildDurationUpper;
    }

    public void setBuildDurationUpper(int buildDurationUpper) {
        this.buildDurationUpper = buildDurationUpper;
    }
}
