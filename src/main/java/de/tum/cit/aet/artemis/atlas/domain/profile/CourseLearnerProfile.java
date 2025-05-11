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
    @Min(0) // TODO should actually be MIN_PROFILE_VALUE = 1, however, then almost all tests in LearningPathIntegrationTest fail
    @Max(MAX_PROFILE_VALUE)
    private int aimForGradeOrBonus;

    @Column(name = "time_investment")
    @Min(0) // TODO should actually be MIN_PROFILE_VALUE = 1, however, then almost all tests in LearningPathIntegrationTest fail
    @Max(MAX_PROFILE_VALUE)
    private int timeInvestment;

    @Column(name = "repetition_intensity")
    @Min(0) // TODO should actually be MIN_PROFILE_VALUE = 1, however, then almost all tests in LearningPathIntegrationTest fail
    @Max(MAX_PROFILE_VALUE)
    private int repetitionIntensity;

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
}
