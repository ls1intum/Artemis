package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A TextClusterStatistics.
 */
@Entity
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextClusterStatistics extends DomainObject {

    @Column(name = "clusterId")
    private Long clusterId;

    @Column(name = "clusterSize")
    private Long clusterSize;

    @Column(name = "numberOfAutomaticFeedbacks")
    private Long numberOfAutomaticFeedbacks;

    @Column(name = "disabled")
    private Boolean disabled;

    public TextClusterStatistics() {
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

    public Boolean getDisabled() {
        return disabled;
    }

    public void setDisabled(Boolean disabled) {
        this.disabled = disabled;
    }

    @Override
    public String toString() {
        return "TextClusterStatistics{" + "clusterId=" + getClusterId() + ", clusterSize='" + getClusterSize() + ", numberOfAutomaticFeedbacks='" + getNumberOfAutomaticFeedbacks()
                + ", disabled='" + getDisabled() + "'}";
    }
}
