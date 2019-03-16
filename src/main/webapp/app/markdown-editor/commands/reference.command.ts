import { Command } from './command';

export class ReferenceCommand extends Command {

    buttonIcon = 'quote-left';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.quote';

    execute(): void {
        if (!this.editor) { return; }
        let selectedText = this.editor.getSelectedText();
        const isSelected = !!selectedText;
        const startSize = 2;
        let initText = '';
        const range = this.editor.selection.getRange();
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
