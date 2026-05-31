import { MathNode } from './math-node.model';
import { RuleConstraint } from './rule-constraint.model';
import { RuleDirection } from './rule-direction.model';

export type Associativity = 'LEFT' | 'NONE';
export type LayoutCategory = 'TERMINAL_NUMBER' | 'TERMINAL_VARIABLE' | 'BINARY_INFIX' | 'FRACTION' | 'PARENTHESES' | 'UNARY_PREFIX';

export interface RewriteRuleModel {
    id: string;
    name: string;
    paletteLatex: string;
    pattern: MathNode;
    template: MathNode;
    /** Whether the rule may be applied in reverse. */
    direction: RuleDirection;
    /** Side conditions checked after a successful match (e.g. {@code c != 0}). Empty / absent for unconditional rules. */
    constraints?: RuleConstraint[];
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
