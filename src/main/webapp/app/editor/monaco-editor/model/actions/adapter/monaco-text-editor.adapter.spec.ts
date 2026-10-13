import { vi } from 'vitest';
import * as monaco from 'monaco-editor';
import { MonacoTextEditorAdapter } from 'app/editor/monaco-editor/model/actions/adapter/monaco-text-editor.adapter';

describe('MonacoTextEditorAdapter', () => {
    describe('addPasteInterceptor', () => {
        let domNode: HTMLDivElement;
        let input: HTMLTextAreaElement;
        let readOnly: boolean;
        let selectionCount: number;
        let fakeEditor: { getDomNode: () => HTMLElement | null; getSelections: () => unknown[]; getOption: () => boolean; pushUndoStop: ReturnType<typeof vi.fn> };
        let pasteAdapter: MonacoTextEditorAdapter;

        beforeEach(() => {
            domNode = document.createElement('div');
            input = document.createElement('textarea');
            domNode.appendChild(input);
            readOnly = false;
            selectionCount = 1;
            fakeEditor = {
                getDomNode: () => domNode,
                getSelections: () => new Array(selectionCount).fill({}),
                getOption: () => readOnly,
                pushUndoStop: vi.fn(),
            };
            pasteAdapter = new MonacoTextEditorAdapter(fakeEditor as unknown as monaco.editor.IStandaloneCodeEditor);
        });

        /** Pastes the text on the inner input element and reports whether the event still reached it. */
        function paste(text: string): { event: Event; reachedInput: boolean } {
            let reachedInput = false;
            input.addEventListener('paste', () => (reachedInput = true), { once: true });
            const event = new Event('paste', { bubbles: true, cancelable: true });
            Object.defineProperty(event, 'clipboardData', { value: { getData: (type: string) => (type === 'text/plain' ? text : '') } });
            input.dispatchEvent(event);
            return { event, reachedInput };
        }

        it('should cancel the paste and keep it from the editor if the handler handles it', () => {
            const handler = vi.fn().mockReturnValue(true);
            pasteAdapter.addPasteInterceptor(handler);

            const { event, reachedInput } = paste('https://x.org');

            expect(handler).toHaveBeenCalledExactlyOnceWith('https://x.org');
            expect(event.defaultPrevented).toBe(true);
            expect(reachedInput).toBe(false);
            expect(fakeEditor.pushUndoStop).toHaveBeenCalledTimes(2);
        });

        it('should pass the paste on to the editor if the handler does not handle it', () => {
            const handler = vi.fn().mockReturnValue(false);
            pasteAdapter.addPasteInterceptor(handler);

            const { event, reachedInput } = paste('hello');

            expect(handler).toHaveBeenCalledExactlyOnceWith('hello');
            expect(event.defaultPrevented).toBe(false);
            expect(reachedInput).toBe(true);
        });

        it.each([
            { case: 'the editor is read-only', setUp: () => (readOnly = true), text: 'https://x.org' },
            { case: 'there are several selections', setUp: () => (selectionCount = 2), text: 'https://x.org' },
            { case: 'nothing was pasted as text', setUp: () => {}, text: '' },
        ])('should not call the handler if $case', ({ setUp, text }) => {
            setUp();
            const handler = vi.fn().mockReturnValue(true);
            pasteAdapter.addPasteInterceptor(handler);

            const { reachedInput } = paste(text);

            expect(handler).not.toHaveBeenCalled();
            expect(reachedInput).toBe(true);
        });

        it('should not call the handler after the interceptor is disposed', () => {
            const handler = vi.fn().mockReturnValue(true);
            pasteAdapter.addPasteInterceptor(handler).dispose();

            const { reachedInput } = paste('https://x.org');

            expect(handler).not.toHaveBeenCalled();
            expect(reachedInput).toBe(true);
        });

        it('should return a disposable that does nothing if the editor has no DOM node', () => {
            fakeEditor.getDomNode = () => null;

            const disposable = pasteAdapter.addPasteInterceptor(vi.fn());

            expect(() => disposable.dispose()).not.toThrow();
        });
    });
});
