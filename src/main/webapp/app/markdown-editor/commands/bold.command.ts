import { Command } from './command';

export class BoldCommand extends Command {
    buttonIcon = 'bold';
    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.bold';

    /**
     * @function execute
     * @desc Make/Remove bold text
     *       1. Check if the selected text includes (**)
     *       2. If included reduce the selected text by these elements and replace the selected text by textToAdd
     *       3. If not included, add (**) before and after the selected text and insert them into the editor
     *       4. Bold markdown appears
     */
    execute(): void {
        const selectedText = this.getSelectedText();
        let textToAdd = '';

        if (selectedText.charAt(0) === '*' && selectedText.charAt(selectedText.length - 1) === '*') {
            textToAdd = selectedText.slice(2, -2);
            this.insertText(textToAdd);
        } else {
            const refinedString = this.ignoreWhiteSpace(selectedText);
            textToAdd = `**${refinedString}**`;
            this.addRefinedTxt(textToAdd);
        }
    }
}
