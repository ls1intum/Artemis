import { Command } from './command';

export class OrderedlistCommand extends Command {

    buttonIcon = 'list-ol';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.orderedList';

    execute(): void {
        if (!this.editor) { return; }
        let selectedText = this.editor.getSelectedText();
        const isSelected = !!selectedText;
        let startSize = 2;
        const initText = '';
        const range = this.editor.selection.getRange();
        selectedText = `1. ${selectedText || initText}`
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
