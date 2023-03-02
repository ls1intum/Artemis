package de.tum.in.www1.artemis.domain.tutorialgroups;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;
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

    @OneToOne(mappedBy = "tutorialGroupsConfiguration")
    @JsonIgnoreProperties(value = "tutorialGroupsConfiguration", allowSetters = true)
    private Course course;

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

    /**
     * If true, tutorial groups channel will be created for each tutorial group. If false, they will not be created.
     */
    @Column(name = "use_tutorial_group_channels")
    @NotNull
    private Boolean useTutorialGroupChannels;

    /**
     * If true, the created tutorial group channels will be public. If false, they will be private.
     */
    @Column(name = "use_public_tutorial_group_channels")
    @NotNull
    private Boolean usePublicTutorialGroupChannels;

    @OneToMany(mappedBy = "tutorialGroupsConfiguration", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnoreProperties(value = "tutorialGroupsConfiguration", allowSetters = true)
    private Set<TutorialGroupFreePeriod> tutorialGroupFreePeriods = new HashSet<>();

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    /**
     * Gets the start date time of the tutorial period of the course in ISO 8601 format
     *
     * @return the start date time of the tutorial period of the course
     */
    public String getTutorialPeriodStartInclusive() {
        return tutorialPeriodStartInclusive;
    }

    /**
     * Set the start date time of the tutorial period. The parameter must be a valid ISO 8601 date string.
     *
     * @param tutorialPeriodStartInclusive the start date of the tutorial period of the course
     */
    public void setTutorialPeriodStartInclusive(String tutorialPeriodStartInclusive) {
        this.tutorialPeriodStartInclusive = tutorialPeriodStartInclusive;
    }

    /**
     * Gets the end date time of the tutorial period of the course in ISO 8601 format
     *
     * @return the end date time of the tutorial period of the course
     */
    public String getTutorialPeriodEndInclusive() {
        return tutorialPeriodEndInclusive;
    }

    /**
     * Set the end date time of the tutorial period. The parameter must be a valid ISO 8601 date string.
     *
     * @param tutorialPeriodEndInclusive the end date of the tutorial period of the course
     */
    public void setTutorialPeriodEndInclusive(String tutorialPeriodEndInclusive) {
        this.tutorialPeriodEndInclusive = tutorialPeriodEndInclusive;
    }

    public Set<TutorialGroupFreePeriod> getTutorialGroupFreePeriods() {
        return tutorialGroupFreePeriods;
    }

    public void setTutorialGroupFreePeriods(Set<TutorialGroupFreePeriod> tutorialGroupFreePeriods) {
        this.tutorialGroupFreePeriods = tutorialGroupFreePeriods;
    }

    public Boolean getUseTutorialGroupChannels() {
        return useTutorialGroupChannels;
    }

    public void setUseTutorialGroupChannels(Boolean useTutorialGroupChannels) {
        this.useTutorialGroupChannels = useTutorialGroupChannels;
    }

    public Boolean getUsePublicTutorialGroupChannels() {
        return usePublicTutorialGroupChannels;
    }

    public void setUsePublicTutorialGroupChannels(Boolean tutorialGroupChannelsPublic) {
        this.usePublicTutorialGroupChannels = tutorialGroupChannelsPublic;
    }
}
