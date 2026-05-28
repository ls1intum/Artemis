import { MathNode } from './math-node.model';
import { StepDirection } from './rule-direction.model';

export interface DerivationStep {
    id?: number;
    stepIndex: number;
    appliedRuleId: string;
    targetNodePath: number[];
    resultExpression: MathNode;
    /** Direction in which the rule was applied. Defaults to FORWARD when unset. */
    direction?: StepDirection;
}
