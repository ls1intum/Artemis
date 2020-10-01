package de.tum.in.www1.artemis.web.rest.dto;

import java.util.*;

import org.jetbrains.annotations.NotNull;

import de.tum.in.www1.artemis.domain.Submission;

public class SubmissionComparisonDTO implements Comparable<SubmissionComparisonDTO> {

    public final Set<Submission> submissions = new HashSet<>();

    public final Map<String, Double> distanceMetrics = new HashMap<>();

    public SubmissionComparisonDTO addAllSubmissions(Collection<? extends Submission> submissions) {
        this.submissions.addAll(submissions);
        return this;
    }

    public SubmissionComparisonDTO putMetric(String key, Double value) {
        distanceMetrics.putIfAbsent(key, value);
        return this;
    }

    public SubmissionComparisonDTO merge(SubmissionComparisonDTO other) {
        if (submissions.equals(other.submissions)) {
            other.distanceMetrics.forEach(this::putMetric);
        }
        return this;
    }

    private double minimumDistance() {
        return distanceMetrics.values().stream().sorted().findFirst().orElse(-1d);
    }

    @Override
    public int compareTo(@NotNull SubmissionComparisonDTO other) {
        return Double.compare(minimumDistance(), other.minimumDistance());
    }

    @Override
    public String toString() {
        return "SubmissionComparisonDTO{submissions=" + submissions.toString() + ", metrics=" + distanceMetrics.toString() + "}";
    }
}
