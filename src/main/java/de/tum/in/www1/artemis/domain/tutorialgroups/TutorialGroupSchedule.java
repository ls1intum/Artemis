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

/**
 * A {@link TutorialGroupSchedule} is a schedule for a {@link TutorialGroup}. It represents a recurrence pattern for {@link TutorialGroupSession}s.
 * Think of it like a recurring calendar event in your calendar app. E.g. a tutorial group might meet every Monday at 10:00 to 12:00 from 2021-01-01 to 2021-06-30.
 * <p>
 * The individual {@link TutorialGroupSession}s are generated from this schedule and stored in the {@link TutorialGroupSession} table.
 */
@Entity
@Table(name = "tutorial_group_schedule")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorialGroupSchedule extends DomainObject {

    @OneToOne
    @JoinColumn(name = "tutorial_group_id")
    private TutorialGroup tutorialGroup;

    /**
     * The day of the week on which the tutorial group meets. 1 = Monday, 2 = Tuesday, ..., 7 = Sunday
     */
    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    /**
     * The time of day at which the tutorial group meets on the {@link #dayOfWeek}.
     * <p>
     * The time is in ISO 8601 format.
     * <p>
     * Note: String to prevent Hibernate from converting it to UTC
     */
    @Column(name = "start_time")
    private String startTime;

    /**
     * The time of day at which the tutorial group meeting ends on the {@link #dayOfWeek}.
     * <p>
     * The time is in ISO 8601 format.
     * <p>
     * Note: String to prevent Hibernate from converting it to UTC
     */
    @Column(name = "end_time")
    private String endTime;

    /**
     * Currently represents weekly recurrence, so 1 means every week, 2 means every other week, etc.
     * <p>
     * E.g. if the tutorial group meets every Monday then the {@link #repetitionFrequency} is 1.
     */
    @Column(name = "repetition_frequency")
    private Integer repetitionFrequency;

    /**
     * The date from which this recurrence pattern starts.
     * <p>
     * For example, if the tutorial group meets every Monday from 2021-01-01 to 2021-06-30, then the {@link #validFromInclusive} is 2021-01-01.
     * The first session will be on 2021-01-04 (Monday) and the last session will be on 2021-06-28 (Monday).
     * <p>
     * The date is in ISO 8601 format.
     * <p>
     * Note: String to prevent Hibernate from converting it to UTC
     */
    @Column(name = "valid_from_inclusive")
    private String validFromInclusive;

    /**
     * The date until which this recurrence pattern is valid.
     * <p>
     * For example, if the tutorial group meets every Monday from 2021-01-01 to 2021-06-30, then the {@link #validToInclusive} is 2021-06-30.
     * The first session will be on 2021-01-04 (Monday) and the last session will be on 2021-06-28 (Monday).
     * <p>
     * The date is in ISO 8601 format.
     * <p>
     * Note: String to prevent Hibernate from converting it to UTC
     */
    @Column(name = "valid_to_inclusive")
    private String validToInclusive;

    /**
     * The location where the tutorial group meets. Can either be a physical location or a link to a video conference.
     */
    @Column(name = "location")
    @Size(max = 2000)
    @Lob
    private String location;

    /**
     * The sessions that were generated from this schedule, i.e. the sessions that follow this recurrence pattern.
     */
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

    /**
     * Sets the start date of the schedule validity. The date must be in ISO 8601 format.
     *
     * @param validFromInclusive start date of the schedule validity
     */
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

    /**
     * Sets the start end of the schedule validity. The date must be in ISO 8601 format.
     *
     * @param validToInclusive end date of the schedule validity
     */
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
