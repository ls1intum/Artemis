import { faLink } from '@fortawesome/free-solid-svg-icons';
import * as monaco from 'monaco-editor';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

interface UrlArguments {
    text: string;
    url: string;
}

export class MonacoUrlAction extends MonacoEditorAction {
    static readonly ID = 'monaco-url.action';
    static readonly DEFAULT_INSERT_TEXT = '[](https://)';

    constructor() {
        super(MonacoUrlAction.ID, 'artemisApp.multipleChoiceQuestion.editor.link', faLink, undefined);
    }

    executeInCurrentEditor(args?: UrlArguments): void {
        super.executeInCurrentEditor(args);
    }

    run(editor: monaco.editor.ICodeEditor, args?: UrlArguments): void {
        if (!args?.text || !args?.url) {
            this.replaceTextAtCurrentSelection(editor, MonacoUrlAction.DEFAULT_INSERT_TEXT);
        } else {
            this.replaceTextAtCurrentSelection(editor, `[${args.text}](${args.url})`);
        }
        editor.focus();
    }
}
