import * as monaco from 'monaco-editor';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

interface ColorArguments {
    color: string;
}

const CLOSE_DELIMITER = '</span>';

/**
 * Action to toggle color text in the editor. It wraps the selected text with the color delimiter, e.g. switching between text and <span class="color">text</span>.
 */
export class MonacoColorAction extends MonacoEditorAction {
    static readonly ID = 'monaco-color.action';

    constructor() {
        super(MonacoColorAction.ID, 'artemisApp.multipleChoiceQuestion.editor.color', undefined, undefined);
    }

    /**
     * Executes the action in the current editor.
     * @param args The color to apply to the selected text. If no color is provided, the default color red is used.
     */
    executeInCurrentEditor(args?: ColorArguments): void {
        super.executeInCurrentEditor(args);
    }

    /**
     * Toggles the color delimiter around the selected text in the editor. If the selected text is already wrapped in a coloring <span>, the delimiter is removed.
     * @param editor The editor in which to toggle color text.
     * @param args The color to apply to the selected text. If no color is provided, the default color red is used.
     */
    run(editor: monaco.editor.ICodeEditor, args?: ColorArguments) {
        const openDelimiter = `<span class="${args?.color ?? 'red'}">`;
        this.toggleDelimiterAroundSelection(editor, openDelimiter, CLOSE_DELIMITER);
        editor.focus();
    }
}
