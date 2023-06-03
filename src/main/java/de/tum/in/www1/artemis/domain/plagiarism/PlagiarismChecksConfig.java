package de.tum.in.www1.artemis.domain.plagiarism;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "plagiarism_checks_config")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class PlagiarismChecksConfig {

    @Id
    private long id;

    private float similarityThreshold;

    private int minimumScore;

    private int minimumSize;

    public PlagiarismChecksConfig() {
    }

    public PlagiarismChecksConfig(float similarityThreshold, int minimumScore, int minimumSize) {
        this.similarityThreshold = similarityThreshold;
        this.minimumScore = minimumScore;
        this.minimumSize = minimumSize;
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

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
