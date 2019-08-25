package de.tum.in.www1.artemis.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Table;

/**
 * GuidedTourSettings
 **/
@Embeddable
@Table(name = "guided_tour_settings")
public class GuidedTourSettings implements Serializable {

    public enum Status {
        STARTED, FINISHED
    }

    @Column(name = "guided_tour_key")
    private String guidedTourKey = null;

    @Column(name = "guided_tour_step")
    private Integer guidedTourStep = 0;

    @Column(name = "guided_tour_state")
    private Status guidedTourState = null;

    public GuidedTourSettings() {
    }

    public String getGuidedTourKey() {
        return guidedTourKey;
    }

    public void setGuidedTourKey(String guidedTourKey) {
        this.guidedTourKey = guidedTourKey;
    }

    public Integer getGuidedTourStep() {
        return guidedTourStep;
    }

    public void setGuidedTourStep(Integer guidedTourStep) {
        this.guidedTourStep = guidedTourStep;
    }

    public Status getGuidedTourState() {
        return guidedTourState;
    }

    public void setGuidedTourState(Status guidedTourState) {
        this.guidedTourState = guidedTourState;
    }

    public GuidedTourSettings guidedTourKey(String guidedTourKey) {
        this.guidedTourKey = guidedTourKey;
        return this;
    }

    public GuidedTourSettings guidedTourStep(Integer guidedTourStep) {
        this.guidedTourStep = guidedTourStep;
        return this;
    }
}
