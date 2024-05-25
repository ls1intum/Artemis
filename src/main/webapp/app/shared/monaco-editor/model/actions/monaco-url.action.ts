import { faLink } from '@fortawesome/free-solid-svg-icons';
import { MonacoEditorInsertAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-insert-action.model';
import * as monaco from 'monaco-editor';

const INSERT_URL_TEXT = '[](https://)';
export class MonacoUrlAction extends MonacoEditorInsertAction {
    static readonly ID = 'monaco-url.action';
    constructor(label: string, translationKey: string) {
        super(MonacoUrlAction.ID, label, translationKey, faLink, undefined, INSERT_URL_TEXT);
    }

    run(editor: monaco.editor.ICodeEditor, args?: { text: string; url: string }): void {
        if (!args?.text || !args?.url) {
            super.run(editor);
        } else {
            this.replaceTextAtCurrentSelection(editor, `[${args.text}](${args.url})`);
            editor.focus();
        }
    }
}
