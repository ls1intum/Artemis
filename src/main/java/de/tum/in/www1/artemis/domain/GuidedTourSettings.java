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

    @Column(name = "guided_tour_key")
    private String guidedTourKey = null;

    @Column(name = "guided_tour_step")
    private Integer guidedTourStep = 0;

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

    public GuidedTourSettings guidedTourKey(String guidedTourKey) {
        this.guidedTourKey = guidedTourKey;
        return this;
    }

    public GuidedTourSettings guidedTourStep(Integer guidedTourStep) {
        this.guidedTourStep = guidedTourStep;
        return this;
    }
}
