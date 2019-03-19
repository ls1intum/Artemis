import { Command } from './command';

export class ReferenceCommand extends Command {

    buttonIcon = 'quote-left';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.quote';

    execute(): void {
        let selectedText = this.editor.getSelectedText();
        let textToAdd = '';

        if (selectedText.includes('>') && !selectedText.includes('Reference')) {
            textToAdd = selectedText.slice(2);
            this.editor.insert(textToAdd);
        } else if (selectedText.includes('>') && selectedText.includes('Reference')) {
            textToAdd = selectedText.slice(2, -9);
            this.editor.insert(textToAdd);
        } else {
            const range = this.editor.selection.getRange();
            const initText = 'Reference';
            selectedText = `> ${selectedText || initText}`;
            this.editor.session.replace(range, selectedText);
            this.editor.focus();
        }
    }
}
