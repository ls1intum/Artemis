import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCode } from '@fortawesome/free-solid-svg-icons';
import { Command } from './command';

export class CodeCommand extends Command {
    buttonIcon = faCode as IconProp;
    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.code';

    /**
     * @function execute
     * @desc 1. check if the selected text starts with '`' and ends with '`'
     *       2. if it does include those elements reduce the selected text by this elements and add replace the selected text by the reduced text
     *       3. if it does not include those add (`) before and after the selected text and add them to the text editor
     *       4. code markdown appears
     */
    execute(): void {
        let selectedText = this.getSelectedText();

        if (selectedText.startsWith('`') && selectedText.endsWith('`')) {
            const textToAdd = selectedText.slice(1, -1);
            this.insertText(textToAdd);
        } else {
            const range = this.getRange();
            selectedText = '`' + selectedText + '`';
            this.replace(range, selectedText);
            this.focus();
        }
    }
}
