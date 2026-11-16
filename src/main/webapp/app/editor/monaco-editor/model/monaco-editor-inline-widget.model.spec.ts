import { describe, expect, it, vi } from 'vitest';
import * as monaco from 'monaco-editor';
import { MonacoEditorLineWidget } from 'app/editor/monaco-editor/model/monaco-editor-inline-widget.model';

describe('MonacoEditorLineWidget', () => {
    const createEditor = (contentLeft: number) => {
        let layoutListener: () => void = () => {};
        const editor = {
            getLayoutInfo: () => ({ contentLeft }),
            onDidLayoutChange: (listener: () => void) => {
                layoutListener = listener;
                return { dispose: vi.fn() };
            },
        } as unknown as monaco.editor.IStandaloneCodeEditor;
        return { editor, layoutChanged: () => layoutListener(), setContentLeft: (value: number) => (contentLeft = value) };
    };

    it('should span the editor by default', () => {
        const node = document.createElement('div');
        new MonacoEditorLineWidget(createEditor(60).editor, 'widget', node, 3);

        expect(node.style.marginLeft).toBe('');
        expect(node.style.width).toBe('100%');
    });

    it('should start where the code starts and follow the gutter when it is asked to', () => {
        const { editor, layoutChanged, setContentLeft } = createEditor(60);
        const node = document.createElement('div');
        new MonacoEditorLineWidget(editor, 'widget', node, 3, true);

        expect(node.style.marginLeft).toBe('60px');
        expect(node.style.width).toBe('calc(100% - 60px)');

        setContentLeft(72);
        layoutChanged();
        expect(node.style.marginLeft).toBe('72px');
        expect(node.style.width).toBe('calc(100% - 72px)');
    });
});
