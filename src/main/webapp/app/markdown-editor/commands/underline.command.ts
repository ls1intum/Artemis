import { Command } from './command';

export class UnderlineCommand extends Command {

    buttonIcon = 'underline';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.underline';

    /**
     * @function execute
     * @desc Add/Remove underline text
     *       1. Check if the selected text includes (<ins>)
     *       2. If included (**) reduce the selected text by these elements and replace the selected text by textToAdd
     *       3. If not included, add (<ins>) before and after the selected text and insert them into the editor
     *       4. Undeline in markdown language appears
     */
    execute(): void {
        const chosenText = this.getSelectedText();
        let textToAdd = '';

        if (chosenText.includes('<ins>')) {
            textToAdd = chosenText.slice(5, -6);
            this.insertText(textToAdd);
        } else {
            textToAdd = `<ins>${chosenText}</ins>`;
            this.insertText(textToAdd);
        }
    }
}
