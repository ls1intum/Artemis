package de.tum.in.www1.artemis.domain.tutorialgroups;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "tutorial_groups_configuration")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorialGroupsConfiguration extends DomainObject {

    @OneToOne
    @JoinColumn(name = "course_id")
    @JsonIgnoreProperties(value = "tutorialGroupsConfiguration", allowSetters = true)
    private Course course;

    @Column(name = "time_zone")
    @NotEmpty
    private String timeZone;

    @Column(name = "tutorial_period_start_inclusive")
    @NotNull
    private LocalDate tutorialPeriodStartInclusive;

    @Column(name = "tutorial_period_end_inclusive")
    @NotNull
    private LocalDate tutorialPeriodEndInclusive;

    @OneToMany(mappedBy = "tutorialGroupsConfiguration", fetch = FetchType.EAGER, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnoreProperties(value = "tutorialGroupsConfiguration", allowSetters = true)
    private Set<TutorialGroupFreePeriod> tutorialGroupFreePeriods = new HashSet<>();

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public LocalDate getTutorialPeriodStartInclusive() {
        return tutorialPeriodStartInclusive;
    }

    public void setTutorialPeriodStartInclusive(LocalDate tutorialPeriodStartInclusive) {
        this.tutorialPeriodStartInclusive = tutorialPeriodStartInclusive;
    }

    public LocalDate getTutorialPeriodEndInclusive() {
        return tutorialPeriodEndInclusive;
    }

    public void setTutorialPeriodEndInclusive(LocalDate tutorialPeriodEndInclusive) {
        this.tutorialPeriodEndInclusive = tutorialPeriodEndInclusive;
    }

    public TutorialGroupsConfiguration() {
    }

    public TutorialGroupsConfiguration(Course course, String timeZone, LocalDate tutorialPeriodStartInclusive, LocalDate tutorialPeriodEndInclusive,
            Set<TutorialGroupFreePeriod> tutorialGroupFreePeriods) {
        this.course = course;
        this.timeZone = timeZone;
        this.tutorialPeriodStartInclusive = tutorialPeriodStartInclusive;
        this.tutorialPeriodEndInclusive = tutorialPeriodEndInclusive;
        this.tutorialGroupFreePeriods = tutorialGroupFreePeriods;
    }

    public Set<TutorialGroupFreePeriod> getTutorialGroupFreePeriods() {
        return tutorialGroupFreePeriods;
    }

    public void setTutorialGroupFreePeriods(Set<TutorialGroupFreePeriod> tutorialGroupFreePeriods) {
        this.tutorialGroupFreePeriods = tutorialGroupFreePeriods;
    }
}
