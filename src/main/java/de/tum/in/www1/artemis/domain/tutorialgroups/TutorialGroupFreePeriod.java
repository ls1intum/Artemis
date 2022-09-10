package de.tum.in.www1.artemis.domain.tutorialgroups;

import java.time.ZonedDateTime;

import javax.persistence.*;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "tutorial_group_free_period")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorialGroupFreePeriod extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "tutorial_groups_configuration_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("tutorialFreePeriods")
    private TutorialGroupsConfiguration tutorialGroupsConfiguration;

    /**
     * NOTE: Stored in UTC in the database
     */
    @Column(name = "start")
    private ZonedDateTime start;

    /**
     * NOTE: Stored in UTC in the database
     */
    @Column(name = "end")
    private ZonedDateTime end;

    @Column(name = "reason")
    @Size(min = 1, max = 256)
    private String reason;

    public TutorialGroupsConfiguration getTutorialGroupsConfiguration() {
        return tutorialGroupsConfiguration;
    }

    public void setTutorialGroupsConfiguration(TutorialGroupsConfiguration tutorialGroupsConfiguration) {
        this.tutorialGroupsConfiguration = tutorialGroupsConfiguration;
    }

    public ZonedDateTime getStart() {
        return start;
    }

    public void setStart(ZonedDateTime start) {
        this.start = start;
    }

    public ZonedDateTime getEnd() {
        return end;
    }

    public void setEnd(ZonedDateTime end) {
        this.end = end;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
