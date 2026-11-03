import { UrlAction } from './url.action';
import { Mock, vi } from 'vitest';
import { TextEditorPosition } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-position.model';
import { TextEditorRange } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-range.model';
import { MonacoTextEditorAdapter } from 'app/editor/monaco-editor/model/actions/adapter/monaco-text-editor.adapter';
import * as monaco from 'monaco-editor';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('UrlAction', () => {
    const action = new UrlAction();

    const editor = new MonacoTextEditorAdapter({} as monaco.editor.IStandaloneCodeEditor);

    beforeEach(() => {
        vi.spyOn(editor, 'replaceTextAtRange').mockReturnValue(undefined);
        vi.spyOn(editor, 'focus').mockReturnValue(undefined);
        vi.spyOn(editor, 'setSelection').mockReturnValue(undefined);
    });

    afterEach(() => {
        // restoreAllMocks (not clearAllMocks) reverts the vi.spyOn wrappers on the shared `editor` to their originals.
        vi.restoreAllMocks();
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

    describe('paste over a selection', () => {
        const translateService = new MockTranslateService() as unknown as TranslateService;
        let disposeInterceptor: Mock<() => void>;
        let pasteHandler: (pastedText: string) => boolean;

        beforeEach(() => {
            disposeInterceptor = vi.fn<() => void>();
            vi.spyOn(editor, 'addAction').mockReturnValue({ dispose: vi.fn() });
            vi.spyOn(editor, 'addPasteInterceptor').mockImplementation((handler) => {
                pasteHandler = handler;
                return { dispose: disposeInterceptor };
            });
            vi.spyOn(editor, 'setPosition').mockReturnValue(undefined);
            action.register(editor, translateService);
        });

        afterEach(() => {
            action.setExecutionGuard(() => true);
            action.dispose();
        });

        /**
         * Selects the given text on one line of a mocked document.
         * @param text The selected text.
         * @param lines The document's lines; by default one line "see the <text> here", in which the text starts at column 9.
         * @param line The 1-based line the selection is on.
         * @param startColumn The column the selection starts at.
         */
        function selectOnLine(text: string, lines = [`see the ${text} here`], line = 1, startColumn = lines[line - 1].indexOf(text) + 1): TextEditorRange {
            const selection = new TextEditorRange(new TextEditorPosition(line, startColumn), new TextEditorPosition(line, startColumn + text.length));
            vi.spyOn(editor, 'getSelection').mockReturnValue(selection);
            vi.spyOn(editor, 'getTextAtRange').mockReturnValue(text);
            vi.spyOn(editor, 'getLineText').mockImplementation((lineNumber) => lines[lineNumber - 1]);
            vi.spyOn(editor, 'getNumberOfLines').mockReturnValue(lines.length);
            return selection;
        }

        it('should wrap the selection in a link to the pasted URL and place the cursor after it', () => {
            const selection = selectOnLine('docs');

            const handled = pasteHandler('https://docs.artemis.tum.de');

            const expectedLink = '[docs](https://docs.artemis.tum.de)';
            expect(handled).toBe(true);
            expect(editor.replaceTextAtRange).toHaveBeenCalledExactlyOnceWith(selection, expectedLink);
            expect(editor.setPosition).toHaveBeenCalledExactlyOnceWith(new TextEditorPosition(1, 9 + expectedLink.length));
        });

        it('should percent-encode parentheses in the pasted URL', () => {
            const selection = selectOnLine('Java');

            pasteHandler('https://en.wikipedia.org/wiki/Java_(programming_language)');

            expect(editor.replaceTextAtRange).toHaveBeenCalledExactlyOnceWith(selection, '[Java](https://en.wikipedia.org/wiki/Java_%28programming_language%29)');
        });

        it('should trim whitespace around the pasted URL', () => {
            const selection = selectOnLine('docs');

            expect(pasteHandler('  https://x.org/a \n')).toBe(true);

            expect(editor.replaceTextAtRange).toHaveBeenCalledExactlyOnceWith(selection, '[docs](https://x.org/a)');
        });

        it.each([
            { selectedText: 'Java (programming language)', label: 'Java (programming language)' },
            { selectedText: '[Java] (language)', label: '\\[Java\\] (language)' },
            { selectedText: '()', label: '()' },
            { selectedText: 'C:\\temp\\', label: 'C:\\\\temp\\\\' },
        ])('should keep the selected text $selectedText as the label and only escape its backslashes and square brackets', ({ selectedText, label }) => {
            const selection = selectOnLine(selectedText);

            expect(pasteHandler('https://x.org')).toBe(true);

            expect(editor.replaceTextAtRange).toHaveBeenCalledExactlyOnceWith(selection, `[${label}](https://x.org)`);
        });

        it('should not handle the paste if nothing is selected', () => {
            selectOnLine('');

            expect(pasteHandler('https://x.org')).toBe(false);
            expect(editor.replaceTextAtRange).not.toHaveBeenCalled();
        });

        it('should not handle the paste if the selection spans several lines', () => {
            const selection = new TextEditorRange(new TextEditorPosition(1, 3), new TextEditorPosition(2, 4));
            vi.spyOn(editor, 'getSelection').mockReturnValue(selection);
            vi.spyOn(editor, 'getTextAtRange').mockReturnValue('first\nsecond');

            expect(pasteHandler('https://x.org')).toBe(false);
            expect(editor.replaceTextAtRange).not.toHaveBeenCalled();
        });

        it.each(['hello', 'www.example.com', 'ftp://x.org', 'mailto:a@b.de', 'javascript:alert(1)'])('should not handle the paste of %s, which is no http(s) URL', (pastedText) => {
            selectOnLine('docs');

            expect(pasteHandler(pastedText)).toBe(false);
            expect(editor.replaceTextAtRange).not.toHaveBeenCalled();
        });

        it.each(['https://x.org https://y.org', 'https://x.org is great'])('should not handle the paste of "%s", which is more than one URL', (pastedText) => {
            selectOnLine('docs');

            expect(pasteHandler(pastedText)).toBe(false);
            expect(editor.replaceTextAtRange).not.toHaveBeenCalled();
        });

        it.each([
            { selectedText: 'https://x.org', wrapped: false },
            { selectedText: 'www.example.com', wrapped: true },
        ])('should wrap the selected text $selectedText only if it is no http(s) URL', ({ selectedText, wrapped }) => {
            selectOnLine(selectedText);

            expect(pasteHandler('https://y.org')).toBe(wrapped);
            expect(editor.replaceTextAtRange).toHaveBeenCalledTimes(wrapped ? 1 : 0);
        });

        it('should remove the paste interceptor when disposed, so the action can be registered again', () => {
            action.dispose();

            expect(disposeInterceptor).toHaveBeenCalledOnce();
            expect(() => action.register(editor, translateService)).not.toThrow();
        });

        it('should not handle the paste if only whitespace is selected', () => {
            selectOnLine('   ');

            expect(pasteHandler('https://x.org')).toBe(false);
            expect(editor.replaceTextAtRange).not.toHaveBeenCalled();
        });

        it.each([
            { where: 'the label of an existing link', text: 'univ', lines: ['see the [university](https://a.example) site'] },
            { where: 'the URL placeholder the link button selects', text: 'https://', lines: ['see the [docs](https://) here'] },
            { where: 'an inline code span', text: 'docs', lines: ['run `open docs` here'] },
            { where: 'an inline code span with two backticks', text: 'docs', lines: ['Run ``open docs`` now'] },
            { where: 'an inline code span that contains a backtick', text: 'docs', lines: ['Run ``open ` docs`` now'] },
            { where: 'an inline code span that continues on the next line', text: 'docs', lines: ['Run `open docs', 'now` here'] },
            { where: 'an inline code span that started on the line before', text: 'docs', lines: ['Run `open', 'the docs` now'], line: 2 },
            { where: 'the alt text of an image', text: 'docs', lines: ['see ![the docs](https://a.example/logo.png)'] },
            { where: 'an autolink', text: 'docs', lines: ['see <https://a.example/docs> here'] },
            { where: 'a selection that covers a whole link', text: 'the [tum](https://www.tum.de) site', lines: ['see the [tum](https://www.tum.de) site'] },
            { where: 'a whole indented code line including its indentation', text: '    open docs', lines: ['    open docs'], startColumn: 1 },
            { where: 'a shortcut reference link', text: 'docs', lines: ['See [docs]', '', '[docs]: https://a.example'] },
            { where: 'a collapsed reference link', text: 'docs', lines: ['See [docs][]', '', '[docs]: https://a.example'] },
            { where: 'the label of a full reference link', text: 'docs', lines: ['See [the docs][ref]', '', '[ref]: https://a.example'] },
            { where: 'a link reference definition', text: 'docs', lines: ['See [docs]', '', '[docs]: https://a.example/docs'], line: 3 },
            { where: 'a selection that includes a block quote marker', text: '> see', lines: ['> see the docs'], startColumn: 1 },
            { where: 'the underline of a heading', text: '===', lines: ['Setup', '====='], line: 2, startColumn: 1 },
            { where: 'inline HTML', text: 'docs', lines: ['see <a title="docs">here</a>'] },
            { where: 'a link in a table cell', text: 'docs', lines: ['| a | b |', '| - | - |', '| x | [docs](https://a.example) |'], line: 3 },
            {
                where: 'a link cell after a plain cell with the same text',
                text: 'docs',
                lines: ['| a | b |', '| - | - |', '| docs | [docs](https://a.example) |'],
                line: 3,
                startColumn: 11,
            },
            { where: 'a code cell after a plain cell with the same text', text: 'docs', lines: ['| a | b |', '| - | - |', '| docs | `docs` |'], line: 3, startColumn: 11 },
            { where: 'the label of an HTML anchor', text: 'docs', lines: ['See <a href="https://a.example">docs</a>'] },
            { where: 'the label of an HTML anchor that is not closed', text: 'docs', lines: ['See <a href="https://a.example">the docs'] },
            { where: 'a paragraph that starts with an HTML tag', text: 'docs', lines: ['<div>see the docs</div>'] },
            { where: 'a fenced code block', text: 'docs', lines: ['intro', '```', 'open docs', '```'], line: 3 },
            { where: 'the fence line of a code block', text: 'docs', lines: ['```docs'] },
            { where: 'a four-backtick block after a three-backtick line inside it', text: 'docs', lines: ['````', '```', 'open docs', '````'], line: 3 },
            { where: 'a tilde block after a backtick line inside it', text: 'docs', lines: ['~~~', '```', 'open docs', '~~~'], line: 3 },
            { where: 'a block after a line that only looks like its closing fence', text: 'docs', lines: ['```', '``` java', 'open docs', '```'], line: 3 },
            { where: 'a code block indented by four spaces', text: 'docs', lines: ['    open docs'] },
            { where: 'a code block indented by a tab', text: 'docs', lines: ['intro', '', '\topen docs'], line: 3 },
            { where: 'an indented code block after a blank line inside it', text: 'docs', lines: ['    code', '', '    open docs'], line: 3 },
            { where: 'an indented code block right after a heading', text: 'docs', lines: ['# Setup', '    open docs'], line: 2 },
            { where: 'an indented code block right after a horizontal rule', text: 'docs', lines: ['---', '    open docs'], line: 2 },
            { where: 'an indented code block right after an underlined heading', text: 'docs', lines: ['Setup', '=====', '    open docs'], line: 3 },
            { where: 'a code block inside a list item', text: 'docs', lines: ['- step', '', '        open docs'], line: 3 },
            { where: 'a fenced code block inside a block quote', text: 'docs', lines: ['> ```', '> open docs'], line: 2 },
        ])('should not handle the paste if the selection lies in $where', ({ text, lines, line, startColumn }) => {
            selectOnLine(text, lines, line, startColumn);

            expect(pasteHandler('https://b.example')).toBe(false);
            expect(editor.replaceTextAtRange).not.toHaveBeenCalled();
        });

        it.each([
            { after: 'a four-backtick block that contains a three-backtick line', lines: ['````', '```', '````', 'see the docs'] },
            { after: 'a closing fence longer than the opening one', lines: ['```', 'code', '`````', 'see the docs'] },
            { after: 'a line indented by four spaces, which is no fence', lines: ['    ```', 'see the docs'] },
            { after: 'an indented code block', lines: ['    code', 'see the docs'] },
            { after: 'a tab-indented code block and a blank line', lines: ['\tcode', '', 'see the docs'] },
            { after: 'a paragraph, as an indented lazy continuation of it', lines: ['intro', '    see the docs'] },
            { after: 'a blank line, as an indented paragraph of a list item', lines: ['- step', '', '    see the docs'] },
            { after: 'a closed inline code span with two backticks on the same line', lines: ['Run ``code`` and see the docs'] },
            { after: 'a lone backtick that opens no code span', lines: ['it`s in the docs'] },
            { after: 'brackets that are no link, because no reference is defined for them', lines: ['see [the docs] here'] },
            { after: 'a block quote marker', lines: ['> see the docs'] },
            { after: 'a heading marker', lines: ['## see the docs'] },
            { after: 'a table cell border', lines: ['| a | b |', '| - | - |', '| x | see the docs |'] },
            { after: 'nothing, in a plain cell before a link cell with the same text', lines: ['| a | b |', '| - | - |', '| see the docs | [docs](https://a.example) |'] },
            { after: 'an HTML tag that is no anchor', lines: ['see <b>the</b> docs'] },
            { after: 'a less-than sign that starts no tag', lines: ['if a < b, see the docs'] },
            { after: 'a blank line, as an indented paragraph of a numbered list item', lines: ['1.  step', '', '    see the docs'] },
        ])('should handle the paste in text after $after', ({ lines }) => {
            const selection = selectOnLine('docs', lines, lines.length);

            expect(pasteHandler('https://b.example')).toBe(true);

            expect(editor.replaceTextAtRange).toHaveBeenCalledExactlyOnceWith(selection, '[docs](https://b.example)');
        });

        it('should not handle the paste while the execution guard forbids the action', () => {
            action.setExecutionGuard(() => false);
            selectOnLine('docs');

            expect(pasteHandler('https://b.example')).toBe(false);
            expect(editor.replaceTextAtRange).not.toHaveBeenCalled();
        });

        it('should handle the paste next to an existing link and after a closed code block', () => {
            const lines = ['```', 'code', '```', 'see the docs and [tum](https://www.tum.de) and `code`'];
            const selection = selectOnLine('docs', lines, 4);

            expect(pasteHandler('https://b.example')).toBe(true);

            expect(editor.replaceTextAtRange).toHaveBeenCalledExactlyOnceWith(selection, '[docs](https://b.example)');
        });
    });
});
