import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faItalic } from '@fortawesome/free-solid-svg-icons';
import { Command } from './command';

export class ItalicCommand extends Command {
    buttonIcon = faItalic as IconProp;
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

        if (selectedText.charAt(0) === '*' && selectedText.charAt(selectedText.length - 1) === '*') {
            textToAdd = selectedText.slice(1, -1);
            this.insertText(textToAdd);
        } else {
            const trimmedText = this.deleteWhiteSpace(selectedText);
            textToAdd = `*${trimmedText}*`;
            this.addRefinedText(selectedText, textToAdd);
        }
    }
}
