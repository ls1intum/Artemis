import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import * as monaco from 'monaco-editor';

export abstract class MonacoEditorAction implements monaco.editor.IActionDescriptor, monaco.IDisposable {
    // IActionDescriptor
    id: string;
    label: string;
    translationKey: string;
    keybindings?: number[];

    icon?: IconDefinition;

    /**
     * The disposable action that is returned by `editor.addAction`. This is required to unregister the action from the editor.
     */
    disposableAction?: monaco.IDisposable;

    constructor(id: string, label: string, translationKey: string, icon?: IconDefinition, keybindings?: number[]) {
        this.id = id;
        this.label = label;
        this.translationKey = translationKey;
        this.icon = icon;
        this.keybindings = keybindings;
    }

    // TODO: might be required to support e.g. color picker
    runWithArguments(editor: monaco.editor.ICodeEditor, args: any): void {
        editor.trigger(this.id, this.id, args);
    }

    abstract run(editor: monaco.editor.ICodeEditor, args?: unknown): void;

    /**
     * Dispose the action if it has been registered with an editor.
     */
    dispose(): void {
        this.disposableAction?.dispose();
    }

    /**
     * Register this action with the given editor. This is required to make the action available in the editor. Note that the action can only be registered with one editor at a time.
     * @param editor The editor to register this action with. Note that its type has to be `monaco.editor.IStandaloneCodeEditor` to ensure it supports the `addAction` method.
     * @throws error if the action has already been registered with an editor.
     */
    register(editor: monaco.editor.IStandaloneCodeEditor) {
        if (this.disposableAction) {
            throw new Error(`Action (id ${this.id}) already belongs to an editor. Dispose it first before registering it with another editor.`);
        }
        this.disposableAction = editor.addAction(this);
    }

    replaceTextAtCurrentSelection(editor: monaco.editor.ICodeEditor, text: string): void {
        const selection = editor.getSelection();
        const selectedText = selection ? this.getTextAtRange(editor, selection)?.trim() : undefined;
        if (selection && selectedText !== undefined) {
            this.replaceTextAtRange(editor, selection, text);
        }
    }

    insertTextAtPosition(editor: monaco.editor.ICodeEditor, position: monaco.IPosition, text: string): void {
        this.replaceTextAtRange(editor, new monaco.Range(position.lineNumber, position.column, position.lineNumber, position.column), text);
    }

    replaceTextAtRange(editor: monaco.editor.ICodeEditor, range: monaco.IRange, text: string): void {
        editor.executeEdits(this.id, [{ range, text }]);
    }

    deleteTextAtRange(editor: monaco.editor.ICodeEditor, range: monaco.IRange): void {
        this.replaceTextAtRange(editor, range, '');
    }

    getTextAtRange(editor: monaco.editor.ICodeEditor, range: monaco.IRange): string | undefined {
        // End of line preference is important here. Otherwise, Windows may use CRLF line endings.
        return editor.getModel()?.getValueInRange(range, monaco.editor.EndOfLinePreference.LF);
    }

    getLineText(editor: monaco.editor.ICodeEditor, lineNumber: number): string | undefined {
        return editor.getModel()?.getLineContent(lineNumber);
    }
}
