import * as monaco from 'monaco-editor';
import { MonacoEditorDelimiterAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-delimiter-action.model';
import { faItalic } from '@fortawesome/free-solid-svg-icons';

const ITALIC_DELIMITER = '*';
export class MonacoItalicAction extends MonacoEditorDelimiterAction {
    constructor(label: string, translationKey: string) {
        super('monaco-italic.action', label, translationKey, faItalic, [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyI], ITALIC_DELIMITER, ITALIC_DELIMITER);
    }
}
