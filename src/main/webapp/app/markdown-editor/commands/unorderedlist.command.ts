import { Command } from './command';

export class UnorderedlistCommand extends Command {

    buttonIcon = 'list-ul';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.unorderedList';

    execute(): void {
        let selectedText = this.editor.getSelectedText();
        let textToAdd = '';

        if (selectedText.includes('-')) {
            textToAdd = selectedText.slice(2);
            this.editor.insert(textToAdd);
        } else {
            const initText = '';
            const range = this.editor.selection.getRange();
            selectedText = `- ${selectedText || initText}`;
            this.editor.session.replace(range, selectedText);
            this.editor.focus();
        }
    }
}
