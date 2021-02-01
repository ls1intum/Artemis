package de.tum.in.www1.artemis.domain.plagiarism;

import java.util.List;

import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorType;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.annotations.DiscriminatorOptions;

import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Exercise;

/**
 * Base result of any automatic plagiarism detection.
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "PR")
@DiscriminatorOptions(force = true)
@Table(name = "plagiarism_result")
public abstract class PlagiarismResult<E extends PlagiarismSubmissionElement> extends DomainObject {

    /**
     * TODO: Remove the @Transient annotation and store the comparisons in the database.
     * List of detected comparisons whose similarity is above the specified threshold.
     */
    @Transient
    protected List<PlagiarismComparison<E>> comparisons;

    /**
     * Duration of the plagiarism detection run in milliseconds.
     */
    protected long duration;

    /**
     * ID of the exercise for which plagiarism was detected.
     */
    @JsonIdentityReference(alwaysAsId = true)
    @JsonProperty("exercise_id")
    @ManyToOne
    protected Exercise exercise;

    /**
     * TODO: Remove the @Transient annotation and store the similarity distribution in the database.
     * List of 10 elements representing the similarity distribution of the detected comparisons.
     * <p>
     * Each entry represents the absolute frequency of comparisons whose similarity lies within the
     * respective interval.
     * <p>
     * Intervals: 0: [0% - 10%), 1: [10% - 20%), 2: [20% - 30%), ..., 9: [90% - 100%]
     */
    @Transient
    protected int[] similarityDistribution;

    public List<PlagiarismComparison<E>> getComparisons() {
        return comparisons;
    }

    public void setComparisons(List<PlagiarismComparison<E>> comparisons) {
        this.comparisons = comparisons;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public int[] getSimilarityDistribution() {
        return similarityDistribution;
    }

    public void setSimilarityDistribution(int[] similarityDistribution) {
        this.similarityDistribution = similarityDistribution;
    }
}
