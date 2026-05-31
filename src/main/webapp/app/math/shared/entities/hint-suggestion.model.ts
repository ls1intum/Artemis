import { MathNode } from './math-node.model';

/** Mirror of the backend {@code HintSuggestion} record returned by {@code POST /api/math/exercises/{id}/hints}. */
export interface HintSuggestion {
    ruleId: string;
    path: number[];
    previewResult: MathNode;
    rationale?: string;
}

/** Mirror of the backend {@code ReachabilityReport} returned by {@code GET /api/math/math-exercises/{id}/verify-reachability}. */
export interface ReachabilityReport {
    reachable: boolean;
    initialDistance: number;
    finalDistance: number;
    reducedExpression: MathNode;
}
