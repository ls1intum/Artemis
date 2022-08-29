package de.tum.in.www1.artemis.domain;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@Table(name = "tutorial_groups_configuration")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TutorialGroupsConfiguration extends DomainObject {

    @OneToOne
    @JoinColumn(name = "course_id")
    @JsonIgnoreProperties(value = "tutorialGroupsConfiguration")
    @NotNull
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
}
