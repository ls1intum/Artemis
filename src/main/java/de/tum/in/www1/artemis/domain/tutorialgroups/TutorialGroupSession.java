package de.tum.in.www1.artemis.domain.tutorialgroups;

import static javax.persistence.Persistence.getPersistenceUtil;

import java.time.ZonedDateTime;

import javax.persistence.*;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;

@Entity
@Table(name = "tutorial_group_session")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorialGroupSession extends DomainObject {

    /**
     * NOTE: Stored in UTC in the database, therefore we use ZonedDateTime. Will be converted to UTC by Hibernate.
     */
    @Column(name = "start")
    private ZonedDateTime start;

    /**
     * NOTE: Stored in UTC in the database, therefore we use ZonedDateTime. Will be converted to UTC by Hibernate.
     */
    @Column(name = "end")
    private ZonedDateTime end;

    /**
     * The status of the session. See {@link TutorialGroupSessionStatus} for more information.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TutorialGroupSessionStatus status;

    /**
     * An optional explanation why the session is in the current status.
     * <p>
     * Currently, it is used to explain why a session was cancelled if it is NOT because of an overlap with a {@link TutorialGroupFreePeriod}.
     * For example the reason could be "The tutor is sick";
     */
    @Column(name = "status_explanation")
    @Size(min = 1, max = 256)
    private String statusExplanation;

    /**
     * Where the session takes place. Could be a link to a video conference or a physical location.
     */
    @Column(name = "location")
    @Size(max = 2000)
    @Lob
    private String location;

    /**
     * If the session is a recurring session, this is the  the schedule that generated this session.
     * <p>
     * Will be null if the session is not recurring, meaning an instructor / tutor created it individually.
     */
    @ManyToOne
    @JoinColumn(name = "tutorial_group_schedule_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties(value = "tutorialGroupSessions, tutorialGroup", allowSetters = true)
    private TutorialGroupSchedule tutorialGroupSchedule;

    /**
     * This connection will be set if the session has status {@link TutorialGroupSessionStatus#CANCELLED} because it overlaps with a {@link TutorialGroupFreePeriod}.
     */
    @ManyToOne
    @JoinColumn(name = "tutorial_group_free_period_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties(value = "tutorialGroupsConfiguration", allowSetters = true)
    private TutorialGroupFreePeriod tutorialGroupFreePeriod;

    /**
     * The tutorial group that this session belongs to. Is always set for recurring and non-recurring sessions.
     */
    @ManyToOne
    @JoinColumn(name = "tutorial_group_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties(value = "tutorialGroupSessions", allowSetters = true)
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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public TutorialGroupFreePeriod getTutorialGroupFreePeriod() {
        return tutorialGroupFreePeriod;
    }

    public void setTutorialGroupFreePeriod(TutorialGroupFreePeriod tutorialGroupFreePeriod) {
        this.tutorialGroupFreePeriod = tutorialGroupFreePeriod;
    }

    /**
     * Hides privacy-sensitive information.
     */
    public void hidePrivacySensitiveInformation() {
        if (this.tutorialGroup != null) {
            this.tutorialGroup.hidePrivacySensitiveInformation();
        }
    }

    /**
     * Removes circular references for JSON serialization.
     *
     * @param tutorialGroupSession the tutorial group session to remove circular references for
     * @return the tutorial group session without circular references
     */
    public static TutorialGroupSession preventCircularJsonConversion(TutorialGroupSession tutorialGroupSession) {
        // prevent circular to json conversion
        if (getPersistenceUtil().isLoaded(tutorialGroupSession, "tutorialGroupSchedule") && tutorialGroupSession.getTutorialGroupSchedule() != null) {
            tutorialGroupSession.getTutorialGroupSchedule().setTutorialGroupSessions(null);
            tutorialGroupSession.getTutorialGroupSchedule().setTutorialGroup(null);
        }
        if (getPersistenceUtil().isLoaded(tutorialGroupSession, "tutorialGroup") && tutorialGroupSession.getTutorialGroup() != null) {
            tutorialGroupSession.getTutorialGroup().setTutorialGroupSessions(null);
            tutorialGroupSession.getTutorialGroup().setTutorialGroupSchedule(null);
        }
        return tutorialGroupSession;
    }
}
