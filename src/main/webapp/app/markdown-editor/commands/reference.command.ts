import { Command } from './command';

export class ReferenceCommand extends Command {

    buttonIcon: 'fas fa-quote-left';
    buttonTitle: 'Reference';

    execute(): void {
        if (!this.editor) return;
        let selectedText = this.editor.getSelectedText();
        let isSelected = !!selectedText;
        let startSize = 2;
        let initText: string = '';
        let range = this.editor.selection.getRange();
        initText = 'Refrence';
        selectedText = `> ${selectedText || initText}`;
        this.editor.session.replace(range, selectedText);
        if (!isSelected) {
            range.start.column += startSize;
            range.end.column = range.start.column + initText.length;
            this.editor.selection.setRange(range);
        }
        this.editor.focus();

    }
}
