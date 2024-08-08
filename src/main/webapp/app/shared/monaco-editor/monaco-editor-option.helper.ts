import { MonacoEditorOptionPreset } from 'app/shared/monaco-editor/model/monaco-editor-option-preset.model';

export const SHORT_ANSWER_QUIZ_QUESTION_EDITOR_OPTIONS = new MonacoEditorOptionPreset({
    // Hide the gutter
    lineNumbers: 'off',
    glyphMargin: false,
    folding: false,
    lineNumbersMinChars: 0,
    // Add padding
    lineDecorationsWidth: '1ch',
    padding: {
        top: 5,
    },
    // Disable line highlighting
    renderLineHighlight: 'none',
});
