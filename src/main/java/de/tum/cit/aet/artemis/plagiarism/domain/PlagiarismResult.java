package de.tum.cit.aet.artemis.plagiarism.domain;

import static java.util.Comparator.comparingInt;
import static java.util.Comparator.reverseOrder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.jplag.JPlagResult;
import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Base result of any automatic plagiarism detection.
 */
@Entity
@Table(name = "plagiarism_result")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PlagiarismResult extends AbstractAuditingEntity {

    private static final int ORIGINAL_SIZE = 100;

    private static final int REDUCED_SIZE = 10;

    /**
     * List of detected comparisons whose similarity is above the specified threshold.
     */
    @OneToMany(mappedBy = "plagiarismResult", cascade = CascadeType.ALL, orphanRemoval = true)
    protected Set<PlagiarismComparison> comparisons = new HashSet<>();

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
    @Column(name = "frequency")
    @ElementCollection(fetch = FetchType.EAGER)
    protected Map<Integer, Integer> similarityDistribution;

    public Set<PlagiarismComparison> getComparisons() {
        return comparisons;
    }

    public void setComparisons(Set<PlagiarismComparison> comparisons) {
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
     *
     * @param size the size to which the comparisons should be limited, e.g. 500
     */
    public void sortAndLimit(int size) {
        // we have to use an intermediate variable here, otherwise the compiler complaints due to generics and type erasing
        Stream<PlagiarismComparison> stream = getComparisons().stream().sorted(reverseOrder()).limit(size);
        this.comparisons = stream.collect(Collectors.toSet());
    }

    @Override
    public String toString() {
        return "PlagiarismResult{" + "comparisons=" + comparisons + ", duration=" + duration + ", similarityDistribution=" + similarityDistribution + '}';
    }

    /**
     * converts the given JPlagResult into a PlagiarismResult, only uses the 500 most interesting comparisons based on the highest similarity
     *
     * @param result   the JPlagResult contains comparisons
     * @param exercise the exercise to which the result should belong, either Text or Programming
     */
    public void convertJPlagResult(JPlagResult result, Exercise exercise) {
        // sort and limit the number of comparisons to 500
        var comparisons = result.getComparisons(500);
        // only convert those 500 comparisons to save memory and cpu power
        for (var jPlagComparison : comparisons) {
            var comparison = PlagiarismComparison.fromJPlagComparison(jPlagComparison, exercise, result.getOptions().submissionDirectories().iterator().next());
            comparison.setPlagiarismResult(this);
            this.comparisons.add(comparison);
        }
        this.duration = result.getDuration();

        // Convert JPlag Similarity Distribution from int[100] to int[10]
        int[] tenthPercentileSimilarityDistribution = new int[REDUCED_SIZE];
        for (int i = 0; i < ORIGINAL_SIZE; i++) {
            tenthPercentileSimilarityDistribution[i / REDUCED_SIZE] += result.getSimilarityDistribution()[i];
        }

        this.setSimilarityDistribution(tenthPercentileSimilarityDistribution);
        this.setExercise(exercise);
    }
}
