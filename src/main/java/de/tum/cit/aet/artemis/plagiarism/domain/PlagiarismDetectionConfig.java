package de.tum.cit.aet.artemis.plagiarism.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Stores configuration for manual and continuous plagiarism control.
 */
@Entity
@Table(name = "plagiarism_detection_config")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PlagiarismDetectionConfig extends DomainObject {

    /**
     * The exercise this configuration belongs to. The key lives here rather than on the exercise: the exercise carries no
     * mapped association to its plagiarism detection configuration, so loading an exercise can never pull this row in, and
     * the configuration cannot outlive the exercise. Read it through {@code PlagiarismDetectionConfigRepository} where it is
     * needed.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false, unique = true)
    @JsonIgnore
    @Parent
    private Exercise exercise;

    /**
     * The key of {@link #exercise}, read without touching the lazy association, so that the configurations of many
     * exercises can be matched to their exercises after one query. Written through {@link #exercise} only.
     */
    @JsonIgnore
    @Column(name = "exercise_id", insertable = false, updatable = false)
    private Long exerciseId;

    public PlagiarismDetectionConfig() {
    }

    /**
     * Copies the settings of another configuration. The copy belongs to no exercise yet: the exercise and its key are not
     * taken over, because they identify the source's exercise. The caller attaches the copy to the exercise it is for.
     *
     * @param inputConfig the configuration whose settings are copied
     */
    public PlagiarismDetectionConfig(PlagiarismDetectionConfig inputConfig) {
        this.exercise = null;
        this.exerciseId = null;
        this.continuousPlagiarismControlEnabled = inputConfig.continuousPlagiarismControlEnabled;
        this.continuousPlagiarismControlPostDueDateChecksEnabled = inputConfig.continuousPlagiarismControlPostDueDateChecksEnabled;
        this.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod = inputConfig.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod;
        this.similarityThreshold = inputConfig.similarityThreshold;
        this.minimumScore = inputConfig.minimumScore;
        this.minimumSize = inputConfig.minimumSize;
    }

    @Column(name = "continuous_plagiarism_control_enabled")
    private boolean continuousPlagiarismControlEnabled = false;

    @Column(name = "continuous_plagiarism_control_post_due_date_checks_enabled")
    private boolean continuousPlagiarismControlPostDueDateChecksEnabled = false;

    @Column(name = "continuous_plagiarism_control_case_student_response_period")
    private int continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod = 7;

    @Column(name = "similarity_threshold")
    @Min(0)
    @Max(100)
    private int similarityThreshold;

    /**
     * Minimum score of the submission.
     * This is used to filter out submissions that are not successful enough to be considered for plagiarism detection.
     */
    @Column(name = "minimum_score")
    @Min(0)
    @Max(100)
    private int minimumScore;

    /**
     * Minimum size of the submission in tokens.
     * This is used to filter out submissions that are too small to be considered for plagiarism detection.
     */
    @Column(name = "minimum_size")
    @Min(0)
    private int minimumSize;

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    /**
     * Set all sensitive information to placeholders, so no info about plagiarism checks gets leaked to students through json.
     */
    public void filterSensitiveInformation() {
        continuousPlagiarismControlEnabled = false;
        continuousPlagiarismControlPostDueDateChecksEnabled = false;
        similarityThreshold = -1;
        minimumScore = -1;
        minimumSize = -1;
    }

    public boolean isContinuousPlagiarismControlEnabled() {
        return continuousPlagiarismControlEnabled;
    }

    public void setContinuousPlagiarismControlEnabled(boolean continuousPlagiarismControlEnabled) {
        this.continuousPlagiarismControlEnabled = continuousPlagiarismControlEnabled;
    }

    public boolean isContinuousPlagiarismControlPostDueDateChecksEnabled() {
        return continuousPlagiarismControlPostDueDateChecksEnabled;
    }

    public void setContinuousPlagiarismControlPostDueDateChecksEnabled(boolean continuousPlagiarismControlPostDueDateChecksEnabled) {
        this.continuousPlagiarismControlPostDueDateChecksEnabled = continuousPlagiarismControlPostDueDateChecksEnabled;
    }

    public int getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod() {
        return continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod;
    }

    public void setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(int continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod) {
        this.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod = continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod;
    }

    public int getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(int similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public int getMinimumScore() {
        return minimumScore;
    }

    public void setMinimumScore(int minimumScore) {
        this.minimumScore = minimumScore;
    }

    public int getMinimumSize() {
        return minimumSize;
    }

    public void setMinimumSize(int minimumSize) {
        this.minimumSize = minimumSize;
    }

    /**
     * Creates PlagiarismDetectionConfig with default data
     *
     * @return PlagiarismDetectionConfig with default values
     */
    public static PlagiarismDetectionConfig createDefault() {
        var config = new PlagiarismDetectionConfig();
        config.setContinuousPlagiarismControlEnabled(false);
        config.setContinuousPlagiarismControlPostDueDateChecksEnabled(false);
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(7);
        config.setSimilarityThreshold(90);
        config.setMinimumScore(0);
        config.setMinimumSize(50);
        return config;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        PlagiarismDetectionConfig that = (PlagiarismDetectionConfig) o;
        return continuousPlagiarismControlEnabled == that.continuousPlagiarismControlEnabled
                && continuousPlagiarismControlPostDueDateChecksEnabled == that.continuousPlagiarismControlPostDueDateChecksEnabled
                && continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod == that.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod
                && similarityThreshold == that.similarityThreshold && minimumScore == that.minimumScore && minimumSize == that.minimumSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), continuousPlagiarismControlEnabled, continuousPlagiarismControlPostDueDateChecksEnabled,
                continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod, similarityThreshold, minimumScore, minimumSize);
    }

    @Override
    public String toString() {
        return "PlagiarismDetectionConfig{" + "continuousPlagiarismControlEnabled=" + continuousPlagiarismControlEnabled + ", continuousPlagiarismControlPostDueDateChecksEnabled="
                + continuousPlagiarismControlPostDueDateChecksEnabled + ", continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod="
                + continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod + ", similarityThreshold=" + similarityThreshold + ", minimumScore=" + minimumScore
                + ", minimumSize=" + minimumSize + '}';
    }
}
