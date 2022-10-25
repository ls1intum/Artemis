package de.tum.in.www1.artemis.domain.tutorialgroups;

import static de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil.isIso8601DateString;
import static de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil.isIso8601TimeString;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.persistence.*;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "tutorial_group_schedule")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorialGroupSchedule extends DomainObject {

    @OneToOne
    @JoinColumn(name = "tutorial_group_id")
    private TutorialGroup tutorialGroup;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    /**
     * Note: String to prevent Hibernate from converting it to UTC
     */
    @Column(name = "start_time")
    private String startTime;

    /**
     * Note: String to prevent Hibernate from converting it to UTC
     */
    @Column(name = "end_time")
    private String endTime;

    /**
     * Currently represents weekly recurrence, so 1 means every week, 2 means every other week, etc.
     */
    @Column(name = "repetition_frequency")
    private Integer repetitionFrequency;

    /**
     * Note: String to prevent Hibernate from converting it to UTC
     */
    @Column(name = "valid_from_inclusive")
    private String validFromInclusive;

    /**
     * Note: String to prevent Hibernate from converting it to UTC
     */
    @Column(name = "valid_to_inclusive")
    private String validToInclusive;

    @Column(name = "location")
    @Size(max = 2000)
    @Lob
    private String location;

    @OneToMany(mappedBy = "tutorialGroupSchedule", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = "tutorialGroupSchedule", allowSetters = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private List<TutorialGroupSession> tutorialGroupSessions = new ArrayList<>();

    public boolean sameSchedule(TutorialGroupSchedule other) {
        return Objects.equals(this.dayOfWeek, other.dayOfWeek) && Objects.equals(this.startTime, other.startTime) && Objects.equals(this.endTime, other.endTime)
                && Objects.equals(this.repetitionFrequency, other.repetitionFrequency) && Objects.equals(this.validFromInclusive, other.validFromInclusive)
                && Objects.equals(this.validToInclusive, other.validToInclusive) && Objects.equals(this.location, other.location);
    }

    public List<TutorialGroupSession> getTutorialGroupSessions() {
        return tutorialGroupSessions;
    }

    public void setTutorialGroupSessions(List<TutorialGroupSession> tutorialGroupSessions) {
        this.tutorialGroupSessions = tutorialGroupSessions;
    }

    public TutorialGroupSchedule() {
    }

    public TutorialGroup getTutorialGroup() {
        return tutorialGroup;
    }

    public void setTutorialGroup(TutorialGroup tutorialGroup) {
        this.tutorialGroup = tutorialGroup;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    /**
     * Get the start time of the schedule. The time is in ISO 8601 format.
     * <p>
     * Note: In the time zone of the course.
     *
     * @return the start time of a session created from this schedule
     */
    public String getStartTime() {
        return startTime;
    }

    /**
     * Set the start time of the schedule. The time must be in ISO 8601 format.
     * <p>
     * Note: In the time zone of the course.
     *
     * @param startTime the start time of a session created from this schedule
     */
    public void setStartTime(String startTime) {
        if (isIso8601TimeString(startTime)) {
            this.startTime = startTime;
        }
        else {
            throw new IllegalArgumentException("Start time must be in ISO 8601 format (HH:mm:ss)");
        }
    }

    /**
     * Get the end time of the schedule. The time is in ISO 8601 format.
     * <p>
     * Note: In the time zone of the course.
     *
     * @return the end time of a session created from this schedule
     */
    public String getEndTime() {
        return endTime;
    }

    /**
     * Set the end time of the schedule. The time must be in ISO 8601 format.
     * <p>
     * Note: In the time zone of the course.
     *
     * @param endTime the end time of a session created from this schedule
     */
    public void setEndTime(String endTime) {
        if (isIso8601TimeString(endTime)) {
            this.endTime = endTime;
        }
        else {
            throw new IllegalArgumentException("End time must be in ISO 8601 format (HH:mm:ss)");
        }
    }

    public Integer getRepetitionFrequency() {
        return repetitionFrequency;
    }

    public void setRepetitionFrequency(Integer repetitionFrequency) {
        this.repetitionFrequency = repetitionFrequency;
    }

    public String getValidFromInclusive() {
        return validFromInclusive;
    }

    public void setValidFromInclusive(String validFromInclusive) {
        if (isIso8601DateString(validFromInclusive)) {
            this.validFromInclusive = validFromInclusive;
        }
        else {
            throw new IllegalArgumentException("ValidFromInclusive must be in ISO 8601 format (yyyy-MM-dd)");
        }
    }

    public String getValidToInclusive() {
        return validToInclusive;
    }

    public void setValidToInclusive(String validToInclusive) {
        if (isIso8601DateString(validToInclusive)) {
            this.validToInclusive = validToInclusive;
        }
        else {
            throw new IllegalArgumentException("ValidToInclusive must be in ISO 8601 format (yyyy-MM-dd)");
        }
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
