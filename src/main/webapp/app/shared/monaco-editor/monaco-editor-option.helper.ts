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

export const COMMUNICATION_MARKDOWN_EDITOR_OPTIONS = new MonacoEditorOptionPreset({
    // Sets up the layout to make the editor look more like a text field (no line numbers, margin, or highlights).
    lineNumbers: 'off',
    glyphMargin: false,
    folding: false,
    lineDecorationsWidth: '1ch',
    lineNumbersMinChars: 0,
    padding: {
        top: 5,
    },
    renderLineHighlight: 'none',
    selectionHighlight: false,
    occurrencesHighlight: 'off',
    // Only show scrollbars if required.
    scrollbar: {
        vertical: 'auto',
        horizontal: 'auto',
    },
    overviewRulerLanes: 0,
    hideCursorInOverviewRuler: true,
    // The suggestions from showWords are shared between editors of the same language.
    suggest: {
        showWords: false,
    },
    // Separates the editor suggest widget from the editor's layout. It will stick to the page, but it won't interfere with other elements.
    fixedOverflowWidgets: true,
    // We use the 'simple' strategy for word wraps to prevent performance issues. This prevents us from switching to a different font as the lines would no longer break correctly.
    wrappingStrategy: 'simple',
    wordWrap: 'on',
});

export const STANDARD_MARKDOWN_EDITOR_OPTIONS = new MonacoEditorOptionPreset({
    lineNumbers: 'off',
    glyphMargin: false,
    folding: false,
    lineDecorationsWidth: '1ch',
    lineNumbersMinChars: 0,
    padding: {
        top: 5,
    },
    scrollbar: {
        vertical: 'auto',
        horizontal: 'auto',
    },
    overviewRulerLanes: 0,
    hideCursorInOverviewRuler: true,
});
