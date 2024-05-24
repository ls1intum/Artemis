import * as monaco from 'monaco-editor';
import { MonacoEditorDelimiterAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-delimiter-action.model';
import { faBold } from '@fortawesome/free-solid-svg-icons';

const BOLD_DELIMITER = '**';
export class MonacoBoldAction extends MonacoEditorDelimiterAction {
    constructor(label: string, translationKey: string) {
        super('monaco-bold.action', label, translationKey, faBold, [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyB], BOLD_DELIMITER, BOLD_DELIMITER);
    }
}
