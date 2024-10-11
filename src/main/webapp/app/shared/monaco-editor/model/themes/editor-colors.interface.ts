/**
 * Interface for the colors of the editor.
 * See https://code.visualstudio.com/api/references/theme-color
 * All colors must be in the format '#RRGGBB' or '#RRGGBBAA'.
 */
export interface EditorColors {
    backgroundColor?: string; // editor.background
    foregroundColor?: string; // editor.foreground
    lineNumbers?: {
        foregroundColor?: string; // editorLineNumber.foreground
        activeForegroundColor?: string; // editorLineNumber.activeForeground
        dimmedForegroundColor?: string; // editorLineNumber.dimmedForeground
    };
    lineHighlight?: {
        backgroundColor?: string; // editor.lineHighlightBackground
        borderColor?: string; // editor.lineHighlightBorder
    };
    diff?: {
        insertedLineBackgroundColor?: string; // diffEditor.insertedLineBackground
        insertedTextBackgroundColor?: string; // diffEditor.insertedTextBackground
        removedTextBackgroundColor?: string; // diffEditor.removedTextBackground
        removedLineBackgroundColor?: string; // diffEditor.removedLineBackground
        diagonalFillColor?: string; // diffEditor.diagonalFill
        gutter?: {
            insertedLineBackgroundColor?: string; // diffEditorGutter.insertedLineBackground
            removedLineBackgroundColor?: string; // diffEditorGutter.removedLineBackground
        };
    };
}
