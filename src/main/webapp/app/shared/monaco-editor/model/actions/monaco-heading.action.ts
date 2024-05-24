import * as monaco from 'monaco-editor';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';

const HEADING_CHARACTER = '#';
const HEADING_TEXT = 'Heading';
export class MonacoHeadingAction extends MonacoEditorAction {
    level: number;

    constructor(label: string, translationKey: string, level: number) {
        super(`monaco-heading-${level}.action`, label, translationKey);
        this.level = level;
    }

    run(editor: monaco.editor.ICodeEditor) {
        // TODO: toggle lines instead. define abstract class for toggle action
        const selection = editor.getSelection();
        const selectedText = selection ? this.getTextAtRange(editor, selection) : undefined;
        if (selection && selectedText !== undefined) {
            const headingText = this.getTextWithToggledHeading(selectedText || `${HEADING_TEXT} ${this.level}`, this.level);
            this.replaceTextAtRange(editor, selection, headingText);
        }
    }

    private getTextWithToggledHeading(selectedText: string, level: number): string {
        if (selectedText.startsWith(`${HEADING_CHARACTER.repeat(level)} `)) {
            return selectedText.slice(level + 1);
        }
        return `${HEADING_CHARACTER.repeat(level)} ${selectedText}`;
    }
}
