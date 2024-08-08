import * as monaco from 'monaco-editor';

/**
 * A preset of Monaco Editor options that can be applied to an editor for specific use cases, e.g. short answer quiz questions.
 * Presets are defined in the file monaco-editor-option.helper.ts.
 */
export class MonacoEditorOptionPreset {
    constructor(private options: monaco.editor.IEditorOptions) {}

    /**
     * Update the editor options with the preset options.
     * @param editor The editor to which the options should be applied.
     */
    apply(editor: monaco.editor.IStandaloneCodeEditor): void {
        editor.updateOptions(this.options);
    }
}
