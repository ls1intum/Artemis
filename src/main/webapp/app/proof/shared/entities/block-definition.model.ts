import { MathNode } from './math-node.model';

export type Associativity = 'LEFT' | 'NONE';
export type LayoutCategory = 'TERMINAL_NUMBER' | 'TERMINAL_VARIABLE' | 'BINARY_INFIX' | 'FRACTION' | 'PARENTHESES';

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
    precedence?: number;
    associativity?: Associativity;
    layoutCategory?: LayoutCategory;
    displaySymbol?: string;
    latexSymbol?: string;
}
