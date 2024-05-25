import * as monaco from 'monaco-editor';
import { MonacoEditorDelimiterAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-delimiter-action.model';
import { faItalic } from '@fortawesome/free-solid-svg-icons';

const ITALIC_DELIMITER = '*';
export class MonacoItalicAction extends MonacoEditorDelimiterAction {
    static readonly ID = 'monaco-italic.action';
    constructor(label: string, translationKey: string) {
        super(MonacoItalicAction.ID, label, translationKey, faItalic, [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyI], ITALIC_DELIMITER, ITALIC_DELIMITER);
    }
}
