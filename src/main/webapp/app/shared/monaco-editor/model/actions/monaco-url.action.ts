import { faLink } from '@fortawesome/free-solid-svg-icons';
import * as monaco from 'monaco-editor';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

const INSERT_URL_TEXT = '[](https://)';
export class MonacoUrlAction extends MonacoEditorAction {
    static readonly ID = 'monaco-url.action';
    constructor() {
        super(MonacoUrlAction.ID, 'artemisApp.multipleChoiceQuestion.editor.link', faLink, undefined);
    }

    run(editor: monaco.editor.ICodeEditor, args?: { text: string; url: string }): void {
        if (!args?.text || !args?.url) {
            this.replaceTextAtCurrentSelection(editor, INSERT_URL_TEXT);
        } else {
            this.replaceTextAtCurrentSelection(editor, `[${args.text}](${args.url})`);
        }
        editor.focus();
    }
}
