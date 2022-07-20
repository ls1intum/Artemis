package de.tum.in.www1.artemis.domain.plagiarism;

import static java.util.Comparator.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.*;

import org.hibernate.annotations.DiscriminatorOptions;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;

/**
 * Base result of any automatic plagiarism detection.
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("PR")
@DiscriminatorOptions(force = true)
@Table(name = "plagiarism_result")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// Annotation necessary to distinguish between concrete implementations of PlagiarismResults when deserializing from JSON
@JsonSubTypes({ @JsonSubTypes.Type(value = ModelingPlagiarismResult.class, name = "modeling"), @JsonSubTypes.Type(value = TextPlagiarismResult.class, name = "text") })
public abstract class PlagiarismResult<E extends PlagiarismSubmissionElement> extends AbstractAuditingEntity {

    /**
     * List of detected comparisons whose similarity is above the specified threshold.
     */
    @OneToMany(mappedBy = "plagiarismResult", cascade = CascadeType.ALL, orphanRemoval = true, targetEntity = PlagiarismComparison.class)
    protected Set<PlagiarismComparison<E>> comparisons = new HashSet<>();

    /**
     * Duration of the plagiarism detection run in milliseconds.
     */
    @Column(name = "duration")
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

    /**
     * @return an unmodifiable list of the similar distribution
     */
    public List<Integer> getSimilarityDistribution() {
        return this.similarityDistribution.entrySet().stream().sorted(comparingInt(Entry::getKey)).map(Entry::getValue).toList();
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

    /**
     * sort after the comparisons with the highest similarity and limit the number of comparisons to size to prevent too many plagiarism results
     * @param size the size to which the comparisons should be limited, e.g. 500
     */
    public void sortAndLimit(int size) {
        // we have to use an intermediate variable here, otherwise the compiler complaints due to generics and type erasing
        Stream<PlagiarismComparison<E>> stream = getComparisons().stream().sorted(reverseOrder()).limit(size);
        this.comparisons = stream.collect(Collectors.toSet());
    }

    @Override
    public String toString() {
        return "PlagiarismResult{" + "comparisons=" + comparisons + ", duration=" + duration + ", similarityDistribution=" + similarityDistribution + '}';
    }
}
