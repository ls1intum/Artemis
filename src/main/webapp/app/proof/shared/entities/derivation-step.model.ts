import { MathNode } from './math-node.model';

export interface DerivationStep {
    id?: number;
    stepIndex: number;
    appliedRuleId: string;
    targetNodePath: number[];
    resultExpression: MathNode;
}
