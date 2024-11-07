/**
 * Interface for the colors of the editor.
 * See https://code.visualstudio.com/api/references/theme-color
 * All colors must be in the format '#RRGGBB' or '#RRGGBBAA'.
 */
export interface EditorColors {
    /**
     * The background color of the editor.
     */
    backgroundColor?: string;
    /**
     * The default color of all text in the editor, not including syntax highlighting.
     */
    foregroundColor?: string;
    /**
     * Colors for line numbers in the editor.
     */
    lineNumbers?: {
        /**
         * The color of the line numbers.
         */
        foregroundColor?: string;
        /**
         * The color of the line number of the line that the cursor is on.
         */
        activeForegroundColor?: string;
        /**
         * The color of the line numbers for dimmed lines. This is used for the final newline of the code.
         */
        dimmedForegroundColor?: string;
    };
    /**
     * Colors for the active line highlight in the editor.
     */
    lineHighlight?: {
        /**
         * The color used as the background color for the cursor's current line.
         */
        backgroundColor?: string;
        /**
         * The color used for the border of the cursor's current line.
         */
        borderColor?: string;
    };
    /**
     * Colors for the diff editor.
     */
    diff?: {
        /**
         * The background color for inserted lines in the diff editor.
         */
        insertedLineBackgroundColor?: string;
        /**
         * The background color for inserted text in the diff editor.
         * This will overlap with the `insertedLineBackgroundColor`.
         */
        insertedTextBackgroundColor?: string;
        /**
         * The background color for removed lines in the diff editor.
         */
        removedTextBackgroundColor?: string;
        /**
         * The background color for removed text in the diff editor.
         * This will overlap with the `removedLineBackgroundColor`.
         */
        removedLineBackgroundColor?: string;
        /**
         * The color used for the diagonal fill in the diff editor.
         * This is used when the diff editor pads the length of the files to align the lines of the original and modified files.
         */
        diagonalFillColor?: string;
        /**
         * Colors for the diff editor gutter. This is the area to the left of the editor that shows the line numbers.
         */
        gutter?: {
            /**
             * The background color for inserted lines in the diff editor gutter.
             */
            insertedLineBackgroundColor?: string;
            /**
             * The background color for removed lines in the diff editor gutter.
             */
            removedLineBackgroundColor?: string;
        };
    };
}
