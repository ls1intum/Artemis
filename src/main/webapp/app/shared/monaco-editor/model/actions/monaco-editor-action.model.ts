import * as monaco from 'monaco-editor';

export abstract class MonacoEditorAction implements monaco.editor.IActionDescriptor {
    // IActionDescriptor
    id: string;
    label: string;
    translationKey: string;
    keybindings?: number[];

    constructor(id: string, label: string, translationKey: string, keybindings?: number[]) {
        this.id = id;
        this.label = label;
        this.translationKey = translationKey;
        this.keybindings = keybindings;
    }

    abstract run(editor: monaco.editor.ICodeEditor): void;

    insertTextAtPosition(editor: monaco.editor.ICodeEditor, position: monaco.IPosition, text: string): void {
        this.replaceTextAtRange(editor, new monaco.Range(position.lineNumber, position.column, position.lineNumber, position.column), text);
    }

    replaceTextAtRange(editor: monaco.editor.ICodeEditor, range: monaco.IRange, text: string): void {
        editor.executeEdits(this.id, [{ range, text }]);
    }

    getTextAtRange(editor: monaco.editor.ICodeEditor, range: monaco.IRange): string | undefined {
        return editor.getModel()?.getValueInRange(range);
    }
}
