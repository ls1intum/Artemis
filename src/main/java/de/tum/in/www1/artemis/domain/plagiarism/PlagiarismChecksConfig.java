package de.tum.in.www1.artemis.domain.plagiarism;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Exercise;

/**
 * Stores configuration for manual and continuous plagiarism control.
 */
@Entity
@Table(name = "plagiarism_checks_config")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PlagiarismChecksConfig extends DomainObject {

    @OneToOne(mappedBy = "plagiarismChecksConfig", fetch = FetchType.LAZY)
    @JsonIgnoreProperties("plagiarismChecksConfig")
    private Exercise exercise;

    private float similarityThreshold;

    private int minimumScore;

    private int minimumSize;

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
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

    public void setSimilarityThreshold(float similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public void setMinimumScore(int minimumScore) {
        this.minimumScore = minimumScore;
    }

    public void setMinimumSize(int minimumSize) {
        this.minimumSize = minimumSize;
    }

    /**
     * Creates PlagiarismChecksConfig with default data
     */
    public static PlagiarismChecksConfig createDefault() {
        var config = new PlagiarismChecksConfig();
        config.setSimilarityThreshold(0.5f);
        config.setMinimumScore(0);
        config.setMinimumSize(0);
        return config;
    }
}
