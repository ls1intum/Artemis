import * as monaco from 'monaco-editor';
import { faFileCode } from '@fortawesome/free-solid-svg-icons';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

const CODE_BLOCK_DELIMITER = '```';

/**
 * Action to toggle code block in the editor. It wraps the selected text with the code block delimiters and inserts newlines, e.g. for the default language java, switching between text and ```java\n text \n```.
 */
export class MonacoCodeBlockAction extends MonacoEditorAction {
    static readonly ID = 'monaco-code-block.action';

    constructor(private readonly defaultLanguage?: string) {
        super(MonacoCodeBlockAction.ID, 'artemisApp.multipleChoiceQuestion.editor.codeBlock', faFileCode, undefined);
    }

    /**
     * Toggles the code block delimiters around the selected text in the editor. If the selected text is already wrapped in code block delimiters, the delimiter is removed.
     * @param editor The editor in which to toggle code block text.
     */
    run(editor: monaco.editor.ICodeEditor): void {
        this.toggleDelimiterAroundSelection(editor, `${CODE_BLOCK_DELIMITER}${this.defaultLanguage ?? ''}\n`, `\n${CODE_BLOCK_DELIMITER}`);
        editor.focus();
    }
}
