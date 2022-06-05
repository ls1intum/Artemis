import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faUnderline } from '@fortawesome/free-solid-svg-icons';
import { Command } from './command';

export class UnderlineCommand extends Command {
    buttonIcon = faUnderline as IconProp;
    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.underline';

    /**
     * @function execute
     * @desc Add/Remove underline text
     *       1. Check if the selected text includes (<ins>)
     *       2. If included (ins) reduce the selected text by these elements and replace the selected text by textToAdd
     *       3. If not included, add (<ins>) before and after the selected text and insert them into the editor
     *       4. Underline in markdown language appears
     */
    execute(): void {
        const chosenText = this.getSelectedText();

        if (chosenText.includes('<ins>')) {
            const textToAdd = chosenText.slice(5, -6);
            this.insertText(textToAdd);
        } else {
            const textToAdd = `<ins>${chosenText}</ins>`;
            this.insertText(textToAdd);
        }
    }
}
