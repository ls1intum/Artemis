package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This is a dto for providing statistics for the exam instructor dashboard
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextClusterStatisticsDTO {

    private Long clusterId;

    private Long clusterSize;

    private Long numberOfAutomaticFeedbacks;

    private boolean disabled;

    public TextClusterStatisticsDTO(Long clusterId, Long clusterSize, Long numberOfAutomaticFeedbacks, boolean disabled) {
        this.clusterId = clusterId;
        this.clusterSize = clusterSize;
        this.numberOfAutomaticFeedbacks = numberOfAutomaticFeedbacks;
        this.disabled = disabled;
    }

    public Long getClusterId() {
        return clusterId;
    }

    public void setClusterId(Long clusterId) {
        this.clusterId = clusterId;
    }

    public Long getClusterSize() {
        return clusterSize;
    }

    public void setClusterSize(Long clusterSize) {
        this.clusterSize = clusterSize;
    }

    public Long getNumberOfAutomaticFeedbacks() {
        return numberOfAutomaticFeedbacks;
    }

    public void setNumberOfAutomaticFeedbacks(Long numberOfAutomaticFeedbacks) {
        this.numberOfAutomaticFeedbacks = numberOfAutomaticFeedbacks;
    }

    public boolean getDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }
}
