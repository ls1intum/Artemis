package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;

import javax.persistence.*;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;

@Entity
@Table(name = "tutorial_group_session")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorialGroupSession extends DomainObject {

    @Column(name = "start")
    private ZonedDateTime start;

    @Column(name = "end")
    private ZonedDateTime end;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TutorialGroupSessionStatus status;

    @Column(name = "status_explanation")
    @Size(min = 1, max = 256)
    private String statusExplanation;

    @ManyToOne
    @JoinColumn(name = "tutorial_group_schedule_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("tutorialGroupSessions, tutorialGroup")
    private TutorialGroupSchedule tutorialGroupSchedule;

    @ManyToOne
    @JoinColumn(name = "tutorial_group_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("tutorialGroupSessions")
    private TutorialGroup tutorialGroup;

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

    public TutorialGroupSchedule getTutorialGroupSchedule() {
        return tutorialGroupSchedule;
    }

    public void setTutorialGroupSchedule(TutorialGroupSchedule tutorialGroupSchedule) {
        this.tutorialGroupSchedule = tutorialGroupSchedule;
    }

    public TutorialGroup getTutorialGroup() {
        return tutorialGroup;
    }

    public void setTutorialGroup(TutorialGroup tutorialGroup) {
        this.tutorialGroup = tutorialGroup;
    }

    public TutorialGroupSessionStatus getStatus() {
        return status;
    }

    public void setStatus(TutorialGroupSessionStatus tutorialGroupSessionStatus) {
        this.status = tutorialGroupSessionStatus;
    }

    public String getStatusExplanation() {
        return statusExplanation;
    }

    public void setStatusExplanation(String statusExplanation) {
        this.statusExplanation = statusExplanation;
    }
}
