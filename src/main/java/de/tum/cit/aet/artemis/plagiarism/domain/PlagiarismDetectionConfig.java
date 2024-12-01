package de.tum.cit.aet.artemis.plagiarism.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * Stores configuration for manual and continuous plagiarism control.
 */
@Entity
@Table(name = "plagiarism_detection_config")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PlagiarismDetectionConfig extends DomainObject {

    public PlagiarismDetectionConfig() {
    }

    public PlagiarismDetectionConfig(PlagiarismDetectionConfig inputConfig) {
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
    private int similarityThreshold;

    @Column(name = "minimum_score")
    private int minimumScore;

    @Column(name = "minimum_size")
    private int minimumSize;

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
