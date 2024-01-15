import { MathTaskEditorInput, MathTaskEditorInputType } from '../types';

export interface MathTaskExpressionValue {
    expression: string;
}

export type MathTaskExpressionInput = MathTaskEditorInput<MathTaskEditorInputType.EXPRESSION, MathTaskExpressionValue>;
