import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { UrlAction } from './url.action';
import { TextEditorPosition } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-position.model';
import { TextEditorRange } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-range.model';
import { MonacoTextEditorAdapter } from 'app/editor/monaco-editor/model/actions/adapter/monaco-text-editor.adapter';
import * as monaco from 'monaco-editor';

describe('UrlAction', () => {
    setupTestBed({ zoneless: true });

    const action = new UrlAction();

    const editor = new MonacoTextEditorAdapter({} as monaco.editor.IStandaloneCodeEditor);

    beforeEach(() => {
        vi.spyOn(editor, 'replaceTextAtRange').mockReturnValue(undefined);
        vi.spyOn(editor, 'focus').mockReturnValue(undefined);
        vi.spyOn(editor, 'setSelection').mockReturnValue(undefined);
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('should add a placeholder at cursor position if no text is selected', () => {
        const currentPosition = new TextEditorPosition(1, 50);
        vi.spyOn(editor, 'getSelection').mockReturnValue(new TextEditorRange(currentPosition, currentPosition));
        vi.spyOn(editor, 'getPosition').mockReturnValue(currentPosition);
        vi.spyOn(editor, 'getTextAtRange').mockReturnValue('');

        action.run(editor);

        const expectedInsertRange = new TextEditorRange(currentPosition, currentPosition);
        expect(editor.replaceTextAtRange).toHaveBeenCalledWith(expectedInsertRange, UrlAction.DEFAULT_INSERT_TEXT);
    });

    it('should replace, then select placeholder if text is selected', () => {
        const currentPosition = new TextEditorPosition(1, 36);
        const selectionStart = new TextEditorPosition(1, 15);
        const mockText = 'something interesting';
        vi.spyOn(editor, 'getSelection').mockReturnValue(new TextEditorRange(selectionStart, currentPosition));
        vi.spyOn(editor, 'getPosition').mockReturnValue(currentPosition);
        vi.spyOn(editor, 'getTextAtRange').mockReturnValue(mockText);

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
