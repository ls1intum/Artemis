export enum MathTaskEditorInputType {
    EXPRESSION,
    MATRIX,
}

export const MathTaskEditorInputTypes = [MathTaskEditorInputType.EXPRESSION];

export interface MathTaskEditorInput<T extends MathTaskEditorInputType, V> {
    type: T;
    value: V;
}
