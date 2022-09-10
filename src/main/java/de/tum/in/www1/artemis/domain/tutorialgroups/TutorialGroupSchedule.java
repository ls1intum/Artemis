package de.tum.in.www1.artemis.domain.tutorialgroups;

import java.time.LocalDate;
import java.time.LocalTime;
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

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "repetition_frequency")
    private Integer repetitionFrequency;

    /**
     * Note: Just a date in the format YYYY-MM-DD is stored in the database
     */
    @Column(name = "valid_from_inclusive")
    private LocalDate validFromInclusive;

    /**
     * Note: Just a date in the format YYYY-MM-DD is stored in the database
     */
    @Column(name = "valid_to_inclusive")
    private LocalDate validToInclusive;

    @Column(name = "location")
    @Size(max = 2000)
    @Lob
    private String location;

    @OneToMany(mappedBy = "tutorialGroupSchedule", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("tutorialGroupSchedule")
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

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public Integer getRepetitionFrequency() {
        return repetitionFrequency;
    }

    public void setRepetitionFrequency(Integer repetitionFrequency) {
        this.repetitionFrequency = repetitionFrequency;
    }

    public LocalDate getValidFromInclusive() {
        return validFromInclusive;
    }

    public void setValidFromInclusive(LocalDate validFromInclusive) {
        this.validFromInclusive = validFromInclusive;
    }

    public LocalDate getValidToInclusive() {
        return validToInclusive;
    }

    public void setValidToInclusive(LocalDate validToInclusive) {
        this.validToInclusive = validToInclusive;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
