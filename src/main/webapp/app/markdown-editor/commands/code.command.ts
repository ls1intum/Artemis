import { Command } from './command';

export class CodeCommand extends Command {

    buttonIcon =  'code';
    buttonTitle = 'Code';

    execute(): void {
        if (!this.editor) return;
        let selectedText = this.editor.getSelectedText();
        let isSelected = !!selectedText;
        let startSize = 2;
        let initText: string = '';
        let range = this.editor.selection.getRange();
        initText = 'Source Code';
        selectedText = "```language\r\n" + (selectedText || initText) + "\r\n```";
        startSize = 3;
        this.editor.session.replace(range, selectedText);
        if (!isSelected) {
            range.start.column += startSize;
            range.end.column = range.start.column + initText.length;
            this.editor.selection.setRange(range);
        }
        this.editor.focus();

    }
}
