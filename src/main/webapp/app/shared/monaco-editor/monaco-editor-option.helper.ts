import { MonacoEditorOptionPreset } from 'app/shared/monaco-editor/model/monaco-editor-option-preset.model';
import { EditorOptions } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';

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

const defaultMarkdownOptions: EditorOptions = {
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
    // Only show scrollbars if required.
    scrollbar: {
        vertical: 'auto',
        horizontal: 'auto',
    },
    overviewRulerLanes: 0,
    hideCursorInOverviewRuler: true,
    // The suggestions from showWords are shared between editors of the same language, so we disable them.
    suggest: {
        showWords: false,
    },
    // We use the 'simple' strategy for word wraps to prevent performance issues. This prevents us from switching to a different font as the lines would no longer break correctly.
    wordWrap: 'on',
    wrappingStrategy: 'simple',
    selectionHighlight: false,
    occurrencesHighlight: 'off',
};

export const DEFAULT_MARKDOWN_EDITOR_OPTIONS = new MonacoEditorOptionPreset(defaultMarkdownOptions);

export const COMMUNICATION_MARKDOWN_EDITOR_OPTIONS = new MonacoEditorOptionPreset({
    ...defaultMarkdownOptions,
    // Separates the editor suggest widget from the editor's layout. It will stick to the page, but it won't interfere with other elements.
    fixedOverflowWidgets: true,
});
