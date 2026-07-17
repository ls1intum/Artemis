import { vi } from 'vitest';
import { COMMUNICATION_MARKDOWN_EDITOR_OPTIONS } from 'app/editor/monaco-editor/monaco-editor-option.helper';
import { MonacoEditorWithActions } from 'app/editor/monaco-editor/model/actions/monaco-editor.util';

describe('MonacoEditorOptionPresets', () => {
    it('should explicitly enable quick suggestions outside comments and strings in the communication preset', () => {
        const editor = { updateOptions: vi.fn() } as unknown as MonacoEditorWithActions;
        COMMUNICATION_MARKDOWN_EDITOR_OPTIONS.apply(editor);
        expect(editor.updateOptions).toHaveBeenCalledWith(
            expect.objectContaining({
                fixedOverflowWidgets: true,
                quickSuggestions: { other: 'on', comments: 'off', strings: 'off' },
            }),
        );
    });
});
