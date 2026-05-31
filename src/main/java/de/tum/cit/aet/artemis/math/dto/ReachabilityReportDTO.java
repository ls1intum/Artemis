package de.tum.cit.aet.artemis.math.dto;

import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.grader.ReachabilityReport;

/**
 * Serializable view of a {@link ReachabilityReport} returned by
 * {@code GET /api/math/math-exercises/{exerciseId}/verify-reachability}.
 *
 * @param reachable         whether the reducer ended up at the target (distance 0)
 * @param initialDistance   distance between source and target before reduction
 * @param finalDistance     distance between the reduced expression and target
 * @param reducedExpression the tree the reducer settled at (a fixpoint of all forward-only rules)
 */
public record ReachabilityReportDTO(boolean reachable, int initialDistance, int finalDistance, MathNode reducedExpression) {

    /**
     * @param report the grader result to project
     * @return a DTO mirroring the report
     */
    public static ReachabilityReportDTO of(ReachabilityReport report) {
        return new ReachabilityReportDTO(report.reachable(), report.initialDistance(), report.finalDistance(), report.reducedExpression());
    }
}
