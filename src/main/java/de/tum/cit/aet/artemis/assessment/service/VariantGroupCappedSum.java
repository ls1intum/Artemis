package de.tum.cit.aet.artemis.assessment.service;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;

/**
 * Accumulates per-exercise point contributions while enforcing the cap of {@link ExerciseVariantGroup}s.
 * <p>
 * Contributions of exercises that do not belong to a variant group are summed up individually. Contributions of
 * exercises in the same variant group are always summed together first; if the group has a configured cap, the sum is
 * then capped at the group's {@code maxPoints}, so a capped group never contributes more than its cap to the
 * {@link #total()}: {@code min(sum(contributions of the group's variants), cap)}. Groups without a configured cap
 * contribute their raw, uncapped sum.
 */
final class VariantGroupCappedSum {

    private double ungroupedSum = 0.0;

    private final Map<Long, Double> sumPerVariantGroup = new HashMap<>();

    private final Map<Long, Double> capPerVariantGroup = new HashMap<>();

    /**
     * Adds a single exercise's point contribution.
     *
     * @param variantGroupId        the id of the exercise's variant group, or {@code null} if it has none
     * @param variantGroupMaxPoints the group's configured cap, or {@code null} if the exercise has no group or the group has no cap
     * @param value                 the points the exercise contributes
     */
    void add(@Nullable Long variantGroupId, @Nullable Double variantGroupMaxPoints, double value) {
        if (variantGroupId == null) {
            ungroupedSum += value;
            return;
        }
        sumPerVariantGroup.merge(variantGroupId, value, Double::sum);
        if (variantGroupMaxPoints != null) {
            capPerVariantGroup.put(variantGroupId, variantGroupMaxPoints);
        }
    }

    /**
     * @return the sum of all ungrouped contributions plus, for every variant group, the smaller of its summed
     *         contributions and its cap (or the raw sum, for groups without a configured cap)
     */
    double total() {
        double total = ungroupedSum;
        for (var entry : sumPerVariantGroup.entrySet()) {
            total += cappedGroupSum(entry.getKey(), entry.getValue());
        }
        return total;
    }

    /**
     * @return the sum of all contributions without applying any variant group cap (i.e. as if no group had a cap)
     */
    double uncappedTotal() {
        double total = ungroupedSum;
        for (var groupSum : sumPerVariantGroup.values()) {
            total += groupSum;
        }
        return total;
    }

    /**
     * @return the points each variant group contributes, keyed by group id: {@code min(sum(contributions), cap)} for
     *         groups with a configured cap, or the raw sum for groups without one. Only groups that received at least
     *         one contribution are present; ungrouped contributions are not included.
     */
    Map<Long, Double> cappedPointsPerGroup() {
        Map<Long, Double> cappedPerGroup = new HashMap<>();
        for (var entry : sumPerVariantGroup.entrySet()) {
            cappedPerGroup.put(entry.getKey(), cappedGroupSum(entry.getKey(), entry.getValue()));
        }
        return cappedPerGroup;
    }

    private double cappedGroupSum(long variantGroupId, double groupSum) {
        Double cap = capPerVariantGroup.get(variantGroupId);
        return cap != null ? Math.min(groupSum, cap) : groupSum;
    }
}
