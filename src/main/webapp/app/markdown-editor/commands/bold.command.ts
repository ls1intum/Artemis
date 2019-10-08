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
        let updatedTxt = '';

        if (selectedText.includes('**')) {
            textToAdd = selectedText.slice(2, -2);
            this.insertText(textToAdd);
        } else {
            if (selectedText.charAt(0) == ' ' && selectedText.charAt(selectedText.length - 1) == ' ') {
                //eliminates white space at the beginning and end of selected text
                updatedTxt = selectedText.slice(1, selectedText.length - 1);
                textToAdd = `**${updatedTxt}**`;
                this.insertText(' ' + textToAdd + ' ');
            } else if (selectedText.charAt(selectedText.length - 1) == ' ') {
                //eliminates white space at end of selected text
                updatedTxt = selectedText.slice(0, selectedText.length - 1);
                textToAdd = `**${updatedTxt}**`;
                this.insertText(textToAdd + ' ');
            } else if (selectedText.charAt(0) == ' ') {
                //eliminates white space at the beginning of selected text
                updatedTxt = selectedText.slice(1, selectedText.length);
                textToAdd = `**${updatedTxt}**`;
                this.insertText(' ' + textToAdd);
            } else {
                textToAdd = `**${selectedText}**`;
                this.insertText(textToAdd);
            }
        }
    }
}
