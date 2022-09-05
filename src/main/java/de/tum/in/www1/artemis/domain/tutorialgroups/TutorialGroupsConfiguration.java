package de.tum.in.www1.artemis.domain.tutorialgroups;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;

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
    @JsonIgnoreProperties(value = "tutorialGroupsConfiguration")
    private Course course;

    @Column(name = "time_zone")
    @NotEmpty
    private String timeZone;

    @Column(name = "tutorial_period_start_inclusive")
    @NotEmpty
    private String tutorialPeriodStartInclusive;

    @Column(name = "tutorial_period_end_inclusive")
    @NotEmpty
    private String tutorialPeriodEndInclusive;

    @OneToMany(mappedBy = "tutorialGroupsConfiguration", fetch = FetchType.EAGER, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnoreProperties(value = "tutorialGroupsConfiguration", allowSetters = true)
    private Set<TutorialGroupFreeDay> tutorialGroupFreeDays = new HashSet<>();

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

    public String getTutorialPeriodStartInclusive() {
        return tutorialPeriodStartInclusive;
    }

    public void setTutorialPeriodStartInclusive(String tutorialPeriodStartInclusive) {
        this.tutorialPeriodStartInclusive = tutorialPeriodStartInclusive;
    }

    public String getTutorialPeriodEndInclusive() {
        return tutorialPeriodEndInclusive;
    }

    public void setTutorialPeriodEndInclusive(String tutorialPeriodEndInclusive) {
        this.tutorialPeriodEndInclusive = tutorialPeriodEndInclusive;
    }

    public TutorialGroupsConfiguration() {
    }

    public TutorialGroupsConfiguration(Course course, String timeZone, String tutorialPeriodStartInclusive, String tutorialPeriodEndInclusive,
            Set<TutorialGroupFreeDay> tutorialGroupFreeDays) {
        this.course = course;
        this.timeZone = timeZone;
        this.tutorialPeriodStartInclusive = tutorialPeriodStartInclusive;
        this.tutorialPeriodEndInclusive = tutorialPeriodEndInclusive;
        this.tutorialGroupFreeDays = tutorialGroupFreeDays;
    }

    public Set<TutorialGroupFreeDay> getTutorialGroupFreeDays() {
        return tutorialGroupFreeDays;
    }

    public void setTutorialGroupFreeDays(Set<TutorialGroupFreeDay> tutorialGroupFreeDays) {
        this.tutorialGroupFreeDays = tutorialGroupFreeDays;
    }
}
