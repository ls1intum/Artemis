import { faFileCode } from '@fortawesome/free-solid-svg-icons';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';

const CODE_BLOCK_DELIMITER = '```';

/**
 * Action to toggle code block in the editor. It wraps the selected text with the code block delimiters and inserts newlines, e.g. for the default language java, switching between text and ```java\n text \n```.
 */
export class CodeBlockAction extends TextEditorAction {
    static readonly ID = 'code-block.action';

    constructor(private readonly defaultLanguage?: string) {
        super(CodeBlockAction.ID, 'artemisApp.multipleChoiceQuestion.editor.codeBlock', faFileCode, undefined);
    }

    /**
     * Toggles the code block delimiters around the selected text in the editor. If the selected text is already wrapped in code block delimiters, the delimiter is removed.
     * @param editor The editor in which to toggle code block text.
     */
    run(editor: TextEditor): void {
        this.toggleDelimiterAroundSelection(editor, `${CODE_BLOCK_DELIMITER}${this.defaultLanguage ?? ''}\n`, `\n${CODE_BLOCK_DELIMITER}`);
        editor.focus();
    }
}
