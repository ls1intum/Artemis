import * as monaco from 'monaco-editor';
import { MonacoEditorDelimiterAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-delimiter-action.model';

const ITALIC_DELIMITER = '*';
export class MonacoItalicAction extends MonacoEditorDelimiterAction {
    constructor(label: string, translationKey: string) {
        super('monaco-italic.action', label, translationKey, [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyI], ITALIC_DELIMITER, ITALIC_DELIMITER);
    }
}
