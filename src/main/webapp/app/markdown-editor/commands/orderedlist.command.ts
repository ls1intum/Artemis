import { Command } from './command';

export class OrderedlistCommand extends Command {

    buttonIcon = 'list-ol';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.orderedList';

    execute(): void {
        let selectedText = this.editor.getSelectedText();
        let textToAdd = '';

        if (selectedText.includes('1.')) {
            textToAdd = selectedText.slice(2);
            this.editor.insert(textToAdd);
        } else {
            const initText = '';
            const range = this.editor.selection.getRange();
            selectedText = `1. ${selectedText || initText}`;
            this.editor.session.replace(range, selectedText);
            this.editor.focus();
        }
    }
}
