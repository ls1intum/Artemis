package de.tum.in.www1.artemis.domain.plagiarism;

import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "plagiarism_checks_config")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class PlagiarismChecksConfig extends DomainObject {

    private float similarityThreshold;

    private int minimumScore;

    private int minimumSize;

    public PlagiarismChecksConfig() {
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
}
