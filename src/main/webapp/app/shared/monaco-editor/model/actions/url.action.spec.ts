import { UrlAction } from './url.action';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { TextEditorPosition } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-position.model';
import { TextEditorRange } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-range.model';

describe('UrlAction', () => {
    const action = new UrlAction();

    const mockEditor: jest.Mocked<TextEditor> = {
        getSelection: jest.fn(),
        getPosition: jest.fn(),
        replaceTextAtRange: jest.fn(),
        getTextAtRange: jest.fn(),
        focus: jest.fn(),
        setSelection: jest.fn(),
    };

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should add a placeholder at cursor position if no text is selected', () => {
        const currentPosition = new TextEditorPosition(1, 50);
        mockEditor.getSelection.mockReturnValue(null);
        mockEditor.getPosition.mockReturnValue(currentPosition);

        action.run(mockEditor);

        const expectedInsertRange = new TextEditorRange(currentPosition, currentPosition);
        expect(mockEditor.replaceTextAtRange).toHaveBeenCalledWith(expectedInsertRange, UrlAction.DEFAULT_INSERT_TEXT);
    });

    it('should replace, then select placeholder if text is selected', () => {
        const currentPosition = new TextEditorPosition(1, 36);
        const selectionStart = new TextEditorPosition(1, 15);
        const mockText = 'something interesting';
        mockEditor.getSelection.mockReturnValue(new TextEditorRange(selectionStart, currentPosition));
        mockEditor.getPosition.mockReturnValue(currentPosition);
        mockEditor.getTextAtRange.mockReturnValue(mockText);

        action.run(mockEditor);

        const expectedInsertRange = new TextEditorRange(selectionStart, currentPosition);
        const expectedText = `[${mockText}](${UrlAction.DEFAULT_LINK_PLACEHOLDER})`;
        expect(mockEditor.replaceTextAtRange).toHaveBeenCalledWith(expectedInsertRange, expectedText);

        const expectedSelectionStart = new TextEditorPosition(1, 27);
        const expectedSelectionEnd = new TextEditorPosition(1, 35);
        const expectedSelectionRange = new TextEditorRange(expectedSelectionStart, expectedSelectionEnd);
        expect(mockEditor.setSelection).toHaveBeenCalledWith(expectedSelectionRange);
    });
});
