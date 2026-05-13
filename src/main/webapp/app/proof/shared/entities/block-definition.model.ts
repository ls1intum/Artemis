import { MathNode } from './math-node.model';

export interface RewriteRuleModel {
    id: string;
    name: string;
    paletteLatex: string;
    pattern: MathNode;
    template: MathNode;
    isReduction: boolean;
}

export interface BlockDefinitionModel {
    type: string;
    category: string;
    label: string;
    paletteLatex: string;
    slots?: string[];
    rules?: RewriteRuleModel[];
}
