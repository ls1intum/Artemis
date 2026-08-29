import { EditorOptions } from 'app/editor/monaco-editor/model/actions/monaco-editor.util';
import { MonacoEditorOptionPreset } from 'app/editor/monaco-editor/model/monaco-editor-option-preset.model';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

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

export const COMMUNICATION_MARKDOWN_EDITOR_OPTIONS = new MonacoEditorOptionPreset(
    cloneWith(defaultMarkdownOptions, {
        // Separates the editor suggest widget from the editor's layout. It will stick to the page, but it won't interfere with other elements.
        fixedOverflowWidgets: true,
        // Explicitly enable suggestions while typing regular text: the emoji completion relies on quick suggestions
        // to reopen the suggest widget for the first letter typed after ':'. Comments and strings are excluded on purpose.
        quickSuggestions: { other: 'on', comments: 'off', strings: 'off' },
        suggest: {
            // Inherited from the default markdown options: word suggestions are shared between editors of the same language.
            showWords: false,
            // The chat completions carry their own visuals (user names, channel names, emoji glyphs in the label);
            // Monaco's kind icons only add noise in this context.
            showIcons: false,
        },
    }),
);
