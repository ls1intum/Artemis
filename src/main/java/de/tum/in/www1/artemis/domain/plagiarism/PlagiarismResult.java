package de.tum.in.www1.artemis.domain.plagiarism;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorType;
import javax.persistence.DiscriminatorValue;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.DiscriminatorOptions;

import de.tum.in.www1.artemis.domain.AbstractAuditingEntity;
import de.tum.in.www1.artemis.domain.Exercise;

/**
 * Base result of any automatic plagiarism detection.
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("PR")
@DiscriminatorOptions(force = true)
@Table(name = "plagiarism_result")
public abstract class PlagiarismResult<E extends PlagiarismSubmissionElement> extends AbstractAuditingEntity {

    /**
     * List of detected comparisons whose similarity is above the specified threshold.
     */
    @OneToMany(mappedBy = "plagiarismResult", cascade = CascadeType.ALL, orphanRemoval = true, targetEntity = PlagiarismComparison.class)
    protected Set<PlagiarismComparison<E>> comparisons;

    /**
     * Duration of the plagiarism detection run in milliseconds.
     */
    protected long duration;

    /**
     * Exercise for which plagiarism was detected.
     */
    @ManyToOne
    protected Exercise exercise;

    /**
     * List of 10 elements representing the similarity distribution of the detected comparisons.
     * <p>
     * Each entry represents the absolute frequency of comparisons whose similarity lies within the
     * respective interval.
     * <p>
     * Intervals: 0: [0% - 10%), 1: [10% - 20%), 2: [20% - 30%), ..., 9: [90% - 100%]
     */
    @CollectionTable(name = "plagiarism_result_similarity_distribution", joinColumns = @JoinColumn(name = "plagiarism_result_id"))
    @MapKeyColumn(name = "idx")
    @Column(name = "value")
    @ElementCollection(fetch = FetchType.EAGER)
    protected Map<Integer, Integer> similarityDistribution;

    public Set<PlagiarismComparison<E>> getComparisons() {
        return comparisons;
    }

    public void setComparisons(Set<PlagiarismComparison<E>> comparisons) {
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

    public List<Integer> getSimilarityDistribution() {
        return this.similarityDistribution.entrySet().stream().sorted(Comparator.comparingInt(Entry::getKey)).map(Entry::getValue).collect(Collectors.toList());
    }

    /**
     * Because @ElementCollection requires us to use List<Integer> instead of int[], we map the
     * given similarityDistribution argument to a list.
     *
     * @param similarityDistribution 10-element integer array
     */
    public void setSimilarityDistribution(int[] similarityDistribution) {
        this.similarityDistribution = new HashMap<>();

        for (int i = 0; i < similarityDistribution.length; i++) {
            this.similarityDistribution.put(i, similarityDistribution[i]);
        }
    }
}
