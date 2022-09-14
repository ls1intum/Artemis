package de.tum.in.www1.artemis.domain.tutorialgroups;

import static de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil.isIso8601DateString;

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

    /**
     * Note: String to prevent Hibernate from converting it to UTC
     */
    @Column(name = "tutorial_period_start_inclusive")
    @NotNull
    private String tutorialPeriodStartInclusive;

    /**
     * Note: String to prevent Hibernate from converting it to UTC
     */
    @Column(name = "tutorial_period_end_inclusive")
    @NotNull
    private String tutorialPeriodEndInclusive;

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

    public String getTutorialPeriodStartInclusive() {
        return tutorialPeriodStartInclusive;
    }

    public void setTutorialPeriodStartInclusive(String tutorialPeriodStartInclusive) {
        if (isIso8601DateString(tutorialPeriodStartInclusive)) {
            this.tutorialPeriodStartInclusive = tutorialPeriodStartInclusive;
        }
        else {
            throw new IllegalArgumentException("tutorialPeriodStartInclusive must be in ISO 8601 format (yyyy-MM-dd)");
        }
    }

    public String getTutorialPeriodEndInclusive() {
        return tutorialPeriodEndInclusive;
    }

    public void setTutorialPeriodEndInclusive(String tutorialPeriodEndInclusive) {
        if (isIso8601DateString(tutorialPeriodEndInclusive)) {
            this.tutorialPeriodEndInclusive = tutorialPeriodEndInclusive;
        }
        else {
            throw new IllegalArgumentException("tutorialPeriodEndInclusive must be in ISO 8601 format (yyyy-MM-dd)");
        }
    }

    public TutorialGroupsConfiguration() {
    }

    public Set<TutorialGroupFreePeriod> getTutorialGroupFreePeriods() {
        return tutorialGroupFreePeriods;
    }

    public void setTutorialGroupFreePeriods(Set<TutorialGroupFreePeriod> tutorialGroupFreePeriods) {
        this.tutorialGroupFreePeriods = tutorialGroupFreePeriods;
    }
}
