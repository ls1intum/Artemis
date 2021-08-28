package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This is a dto for providing statistics for the text cluster statistics dashboard
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextClusterStatisticsDTO {

    private Long clusterId;

    private Long clusterSize;

    private Long numberOfAutomaticFeedbacks;

    private boolean disabled;

    public TextClusterStatisticsDTO(Long clusterId, Long clusterSize, Long numberOfAutomaticFeedbacks) {
        this.clusterId = clusterId;
        this.clusterSize = clusterSize;
        this.numberOfAutomaticFeedbacks = numberOfAutomaticFeedbacks;
    }

    public TextClusterStatisticsDTO() {
        // needed for Jackson
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

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }
}
