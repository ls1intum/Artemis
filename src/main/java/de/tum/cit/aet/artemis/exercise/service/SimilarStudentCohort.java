package de.tum.cit.aet.artemis.exercise.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Deterministic, tie-preserving quantile groups; no request or course state is retained. */
final class SimilarStudentCohort {

    static final int MINIMUM_PEERS = 10;

    record Cohort(Set<Long> studentIds, int groupCount) {
    }

    private SimilarStudentCohort() {
    }

    static Cohort select(Map<Long, Double> averages, long studentId) {
        Double ownAverage = averages.get(studentId);
        List<Double> sorted = averages.values().stream().filter(Double::isFinite).sorted().toList();
        int groups = sorted.size() >= 4 * (MINIMUM_PEERS + 1) ? 4 : sorted.size() >= 3 * (MINIMUM_PEERS + 1) ? 3 : 0;
        if (ownAverage == null || !Double.isFinite(ownAverage) || groups == 0) {
            return new Cohort(Set.of(), 0);
        }

        List<Double> boundaries = new ArrayList<>();
        double maximum = sorted.getLast();
        for (int group = 1; group < groups; group++) {
            double boundary = sorted.get((int) Math.ceil((double) sorted.size() * group / groups) - 1);
            if (boundary < maximum && !boundaries.contains(boundary)) {
                boundaries.add(boundary);
            }
        }
        if (boundaries.size() < 2) {
            return new Cohort(Set.of(), 0);
        }
        long ownGroup = groupOf(ownAverage, boundaries);
        Set<Long> peers = averages.entrySet().stream()
                .filter(entry -> entry.getKey() != studentId && Double.isFinite(entry.getValue()) && groupOf(entry.getValue(), boundaries) == ownGroup).map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        return peers.size() >= MINIMUM_PEERS ? new Cohort(Set.copyOf(peers), boundaries.size() + 1) : new Cohort(Set.of(), 0);
    }

    private static long groupOf(double average, List<Double> boundaries) {
        return boundaries.stream().filter(boundary -> average > boundary).count();
    }
}
