import * as monaco from 'monaco-editor';
import { MonacoEditorDelimiterAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-delimiter-action.model';

const UNDERLINE_OPEN_DELIMITER = '<ins>';
const UNDERLINE_CLOSE_DELIMITER = '</ins>';
export class MonacoUnderlineAction extends MonacoEditorDelimiterAction {
    constructor(label: string, translationKey: string) {
        super('monaco-underline.action', label, translationKey, [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyU], UNDERLINE_OPEN_DELIMITER, UNDERLINE_CLOSE_DELIMITER);
    }
}
