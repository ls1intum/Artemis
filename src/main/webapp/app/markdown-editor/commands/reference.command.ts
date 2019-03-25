import { Command } from './command';

export class ReferenceCommand extends Command {

    buttonIcon = 'quote-left';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.quote';

    /**
     * @function execute
     * @desc Add/Remove a reference in markdown language
     *       1. Check if the selected text includes ('>') and/or ('Reference')
     *       2. If included reduce the selected text by this elements and add replace the selected text by textToAdd
     *       3. If not included add ('>') before the selected text and insert into editor
     *       4. Reference in markdown appears
     */
    execute(): void {
        let selectedText = this.getSelectedText();
        let textToAdd = '';

        if (selectedText.includes('>') && !selectedText.includes('Reference')) {
            textToAdd = selectedText.slice(2);
            this.insertText(textToAdd);
        } else if (selectedText.includes('>') && selectedText.includes('Reference')) {
            textToAdd = selectedText.slice(2, -9);
            this.insertText(textToAdd);
        } else {
            const range = this.getRange();
            const initText = 'Reference';
            selectedText = `> ${selectedText || initText}`;
            this.replace(range, selectedText);
            this.focus();
        }
    }
}
