import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faHeading } from '@fortawesome/free-solid-svg-icons';
import { Command } from 'app/shared/markdown-editor/commands/command';

export class HeadingTwoCommand extends Command {
    buttonIcon = faHeading as IconProp;
    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.headingTwo';

    /**
     * @function execute
     * @desc Create/Remove heading two language
     *       1. Check if the selected text includes (##) and/or ('Heading 2')
     *       2. If included  reduce the selected text by this elements and add replace the selected text by textToAdd
     *       3. If not included add (##) before the selected text and insert them into the editor
     *       4. Heading two in markdown language appears
     */
    execute(): void {
        let selectedText = this.getSelectedText();

        if (selectedText.includes('##') && !selectedText.includes('Heading 2')) {
            const textToAdd = selectedText.slice(3);
            this.insertText(textToAdd);
        } else if (selectedText.includes('##') && selectedText.includes('Heading 2')) {
            const textToAdd = selectedText.slice(3, -9);
            this.insertText(textToAdd);
        } else {
            const initText = 'Heading 2';
            const range = this.getRange();
            selectedText = `## ${selectedText || initText}`;
            this.replace(range, selectedText);
            this.focus();
        }
    }
}
