package de.tum.cit.aet.artemis.atlas.domain.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "course_learner_profile")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseLearnerProfile extends DomainObject {

    public static final String ENTITY_NAME = "courseLearnerProfile";

    /**
     * Minimum value allowed for profile fields representing values on a Likert scale.
     */
    public static final int MIN_PROFILE_VALUE = 1;

    /**
     * Maximum value allowed for profile fields representing values on a Likert scale.
     */
    public static final int MAX_PROFILE_VALUE = 5;

    @ManyToOne
    @JoinColumn(name = "learner_profile_id")
    private LearnerProfile learnerProfile;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "aim_for_grade_or_bonus")
    @Min(MIN_PROFILE_VALUE)
    @Max(MAX_PROFILE_VALUE)
    private int aimForGradeOrBonus = 3;

    @Column(name = "time_investment")
    @Min(MIN_PROFILE_VALUE)
    @Max(MAX_PROFILE_VALUE)
    private int timeInvestment = 3;

    @Column(name = "repetition_intensity")
    @Min(MIN_PROFILE_VALUE)
    @Max(MAX_PROFILE_VALUE)
    private int repetitionIntensity = 3;

    @Column(name = "proficiency")
    @Min(MIN_PROFILE_VALUE)
    @Max(MAX_PROFILE_VALUE)
    private double proficiency = 3;

    @Column(name = "initial_proficiency")
    @Min(MIN_PROFILE_VALUE)
    @Max(MAX_PROFILE_VALUE)
    private double initialProficiency = 3;

    public void setLearnerProfile(LearnerProfile learnerProfile) {
        this.learnerProfile = learnerProfile;
    }

    public LearnerProfile getLearnerProfile() {
        return this.learnerProfile;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Course getCourse() {
        return this.course;
    }

    public int getAimForGradeOrBonus() {
        return aimForGradeOrBonus;
    }

    public void setAimForGradeOrBonus(int aimForGradeOrBonus) {
        this.aimForGradeOrBonus = aimForGradeOrBonus;
    }

    public int getTimeInvestment() {
        return timeInvestment;
    }

    public void setTimeInvestment(int timeInvestment) {
        this.timeInvestment = timeInvestment;
    }

    public int getRepetitionIntensity() {
        return repetitionIntensity;
    }

    public void setRepetitionIntensity(int repetitionIntensity) {
        this.repetitionIntensity = repetitionIntensity;
    }

    public double getProficiency() {
        return proficiency;
    }

    public void setProficiency(double proficiency) {
        this.proficiency = proficiency;
    }

    public double getInitialProficiency() {
        return initialProficiency;
    }

    public void setInitialProficiency(double initialProficiency) {
        this.initialProficiency = initialProficiency;
    }
}
