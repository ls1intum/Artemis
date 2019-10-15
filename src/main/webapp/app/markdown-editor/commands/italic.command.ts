import { Command } from './command';

export class ItalicCommand extends Command {
    buttonIcon = 'italic';
    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.italic';

    /**
     * @function execute
     * @desc Make/Remove italic text
     *       1. Check if the selected text includes (*)
     *       2. If included reduce the selected text by this elements and replace the selected text by textToAdd
     *       3. If not included add (*) before and after the selected text and insert them into the editor
     *       4. Italic in markdown language appears
     */
    execute(): void {
        const selectedText = this.getSelectedText();
        let textToAdd = '';

        if (selectedText.includes('*')) {
            textToAdd = selectedText.slice(1, -1);
            this.insertText(textToAdd);
        } else {
            const refinedString = this.ignoreWhiteSpace(selectedText);
            const newTxt = refinedString[0];
            const startSpace = refinedString[1];
            const endSpace = refinedString[2];
            textToAdd = `*${newTxt}*`;
            this.insertText(startSpace + textToAdd + endSpace);
        }
    }
}
