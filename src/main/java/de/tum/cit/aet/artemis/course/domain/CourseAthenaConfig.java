package de.tum.cit.aet.artemis.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;

@Entity
@Table(name = "course_athena_config")
public class CourseAthenaConfig extends DomainObject {

    /**
     * Value of {@link #defaultFeedbackDetail} and {@link #defaultFeedbackFormality} meaning "no course default":
     * a student without their own preference falls back to the built-in default instead.
     */
    public static final int FEEDBACK_STYLE_NOT_SET = 0;

    /**
     * Matches {@code LearnerProfile.MAX_PROFILE_VALUE}: the two fields below are sent to Athena on the same 1-3 scale
     * as a student's own feedback preference, so an instructor default and a student override are comparable values.
     */
    public static final int MAX_FEEDBACK_STYLE_VALUE = 3;

    /**
     * The course this configuration belongs to. The key lives here rather than on the course: the course carries no
     * mapped association to its Athena configuration, so loading a course can never pull this row in, and the
     * configuration cannot outlive the course. Read it through {@code CourseAthenaConfigRepository} where it is needed.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, unique = true)
    @JsonIgnore
    @Parent
    private Course course;

    @Column(name = "grading_feedback_enabled", nullable = false)
    private boolean gradingFeedbackEnabled = false;

    @Column(name = "formative_feedback_enabled", nullable = false)
    private boolean formativeFeedbackEnabled = false;

    @Column(name = "default_feedback_detail", nullable = false)
    @Min(FEEDBACK_STYLE_NOT_SET)
    @Max(MAX_FEEDBACK_STYLE_VALUE)
    private int defaultFeedbackDetail = FEEDBACK_STYLE_NOT_SET;

    @Column(name = "default_feedback_formality", nullable = false)
    @Min(FEEDBACK_STYLE_NOT_SET)
    @Max(MAX_FEEDBACK_STYLE_VALUE)
    private int defaultFeedbackFormality = FEEDBACK_STYLE_NOT_SET;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public boolean isGradingFeedbackEnabled() {
        return gradingFeedbackEnabled;
    }

    public void setGradingFeedbackEnabled(boolean gradingFeedbackEnabled) {
        this.gradingFeedbackEnabled = gradingFeedbackEnabled;
    }

    public boolean isFormativeFeedbackEnabled() {
        return formativeFeedbackEnabled;
    }

    public void setFormativeFeedbackEnabled(boolean formativeFeedbackEnabled) {
        this.formativeFeedbackEnabled = formativeFeedbackEnabled;
    }

    public int getDefaultFeedbackDetail() {
        return defaultFeedbackDetail;
    }

    public void setDefaultFeedbackDetail(int defaultFeedbackDetail) {
        this.defaultFeedbackDetail = defaultFeedbackDetail;
    }

    public int getDefaultFeedbackFormality() {
        return defaultFeedbackFormality;
    }

    public void setDefaultFeedbackFormality(int defaultFeedbackFormality) {
        this.defaultFeedbackFormality = defaultFeedbackFormality;
    }
}
