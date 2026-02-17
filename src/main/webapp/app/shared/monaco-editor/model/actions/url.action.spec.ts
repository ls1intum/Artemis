import { UrlAction } from './url.action';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { TextEditorPosition } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-position.model';
import { TextEditorRange } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-range.model';
import { MonacoTextEditorAdapter } from 'app/shared/monaco-editor/model/actions/adapter/monaco-text-editor.adapter';
import * as monaco from 'monaco-editor';

describe('UrlAction', () => {
    const action = new UrlAction();

    const editor: TextEditor = new MonacoTextEditorAdapter(monaco.editor.IStandaloneCodeEditor);

    beforeEach(() => {
        jest.spyOn(editor, 'replaceTextAtRange').mockReturnValue(null);
        jest.spyOn(editor, 'focus').mockReturnValue(null);
        jest.spyOn(editor, 'setSelection').mockReturnValue(null);
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should add a placeholder at cursor position if no text is selected', () => {
        const currentPosition = new TextEditorPosition(1, 50);
        jest.spyOn(editor, 'getSelection').mockReturnValue(null);
        jest.spyOn(editor, 'getPosition').mockReturnValue(currentPosition);

        action.run(editor);

        const expectedInsertRange = new TextEditorRange(currentPosition, currentPosition);
        expect(editor.replaceTextAtRange).toHaveBeenCalledWith(expectedInsertRange, UrlAction.DEFAULT_INSERT_TEXT);
    });

    it('should replace, then select placeholder if text is selected', () => {
        const currentPosition = new TextEditorPosition(1, 36);
        const selectionStart = new TextEditorPosition(1, 15);
        const mockText = 'something interesting';
        jest.spyOn(editor, 'getSelection').mockReturnValue(new TextEditorRange(selectionStart, currentPosition));
        jest.spyOn(editor, 'getPosition').mockReturnValue(currentPosition);
        jest.spyOn(editor, 'getTextAtRange').mockReturnValue(mockText);

        action.run(editor);

        const expectedInsertRange = new TextEditorRange(selectionStart, currentPosition);
        const expectedText = `[${mockText}](${UrlAction.DEFAULT_LINK_PLACEHOLDER})`;
        expect(editor.replaceTextAtRange).toHaveBeenCalledWith(expectedInsertRange, expectedText);

        const expectedSelectionStart = new TextEditorPosition(1, 27);
        const expectedSelectionEnd = new TextEditorPosition(1, 35);
        const expectedSelectionRange = new TextEditorRange(expectedSelectionStart, expectedSelectionEnd);
        expect(editor.setSelection).toHaveBeenCalledWith(expectedSelectionRange);
    });
});
