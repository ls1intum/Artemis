package de.tum.in.www1.artemis.domain;

import java.io.Serializable;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "guided_tour_setting")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GuidedTourSetting implements Serializable {

    public enum Status {
        STARTED, FINISHED
    }

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guided_tour_key")
    private String guidedTourKey;

    @Column(name = "guided_tour_step")
    private Integer guidedTourStep;

    @Column(name = "guided_tour_state")
    private Status guidedTourState;

    @ManyToOne
    @JsonIgnore
    private User user;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public GuidedTourSetting guidedTourKey(String guidedTourKey) {
        this.guidedTourKey = guidedTourKey;
        return this;
    }

    public GuidedTourSetting guidedTourStep(Integer guidedTourStep) {
        this.guidedTourStep = guidedTourStep;
        return this;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public String toString() {
        return "GuidedTourSetting{" + "id=" + id + ", guidedTourKey='" + guidedTourKey + '\'' + ", guidedTourStep=" + guidedTourStep + ", guidedTourState=" + guidedTourState
                + ", user=" + user + '}';
    }
}
