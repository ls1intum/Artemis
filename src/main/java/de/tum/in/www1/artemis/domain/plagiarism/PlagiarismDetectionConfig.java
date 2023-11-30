package de.tum.in.www1.artemis.domain.plagiarism;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * Stores configuration for manual and continuous plagiarism control.
 */
@Entity
@Table(name = "plagiarism_detection_config")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PlagiarismDetectionConfig extends DomainObject {

    @Column(name = "continuous_plagiarism_control_enabled")
    private boolean continuousPlagiarismControlEnabled = false;

    @Column(name = "continuous_plagiarism_control_post_due_date_checks_enabled")
    private boolean continuousPlagiarismControlPostDueDateChecksEnabled = false;

    @Column(name = "similarity_threshold")
    private int similarityThreshold;

    @Column(name = "minimum_score")
    private int minimumScore;

    @Column(name = "minimum_size")
    private int minimumSize;

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

    public float getSimilarityThreshold() {
        return similarityThreshold;
    }

    public int getMinimumScore() {
        return minimumScore;
    }

    public int getMinimumSize() {
        return minimumSize;
    }

    public void setSimilarityThreshold(int similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public void setMinimumScore(int minimumScore) {
        this.minimumScore = minimumScore;
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
                && Float.compare(similarityThreshold, that.similarityThreshold) == 0 && minimumScore == that.minimumScore && minimumSize == that.minimumSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), continuousPlagiarismControlEnabled, continuousPlagiarismControlPostDueDateChecksEnabled, similarityThreshold, minimumScore,
                minimumSize);
    }

    @Override
    public String toString() {
        return "PlagiarismDetectionConfig{continuousPlagiarismControlEnabled=" + continuousPlagiarismControlEnabled + ", continuousPlagiarismControlPostDueDateChecksEnabled="
                + continuousPlagiarismControlPostDueDateChecksEnabled + ", similarityThreshold=" + similarityThreshold + ", minimumScore=" + minimumScore + ", minimumSize="
                + minimumSize + '}';
    }
}
