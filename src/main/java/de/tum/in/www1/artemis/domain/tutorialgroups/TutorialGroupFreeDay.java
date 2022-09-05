package de.tum.in.www1.artemis.domain.tutorialgroups;

import java.time.LocalDate;

import javax.persistence.*;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "tutorial_group_free_day")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorialGroupFreeDay extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "tutorial_groups_configuration_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("tutorialFreeDays")
    private TutorialGroupsConfiguration tutorialGroupsConfiguration;

    @Column(name = "date")
    private LocalDate date;

    @Column(name = "reason")
    @Size(min = 1, max = 256)
    private String reason;

    public TutorialGroupsConfiguration getTutorialGroupsConfiguration() {
        return tutorialGroupsConfiguration;
    }

    public void setTutorialGroupsConfiguration(TutorialGroupsConfiguration tutorialGroupsConfiguration) {
        this.tutorialGroupsConfiguration = tutorialGroupsConfiguration;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
