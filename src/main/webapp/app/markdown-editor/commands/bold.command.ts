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
        let startSpace = '';
        let endSpace = '';
        let startIndx = 0;
        let endIndx = 0;
        const selectedTextLength = selectedText.length;

        if (selectedText.includes('**')) {
            textToAdd = selectedText.slice(2, -2);
            this.insertText(textToAdd);
        } else {
            for (let i = 0; i < selectedTextLength; i++) {
                if (selectedText.charAt(i) === ' ') {
                    startSpace = startSpace + ' ';
                } else {
                    startIndx = i;
                    break;
                }
            }

            for (let j = selectedTextLength; j >= 0; j--) {
                if (selectedText.charAt(j - 1) === ' ') {
                    endSpace = endSpace + ' ';
                } else {
                    endIndx = j;
                    break;
                }
            }
            const newTxt = selectedText.slice(startIndx, endIndx);
            textToAdd = `**${newTxt}**`;
            this.insertText(startSpace + textToAdd + endSpace);
        }
    }
}
