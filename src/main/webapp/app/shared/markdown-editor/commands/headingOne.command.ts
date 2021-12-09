import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faHeading } from '@fortawesome/free-solid-svg-icons';
import { Command } from 'app/shared/markdown-editor/commands/command';

export class HeadingOneCommand extends Command {
    buttonIcon = faHeading as IconProp;
    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.headingOne';

    /**
     * @function execute
     * @desc Create/remove heading one in markdown language
     *       1. Check if the selected text includes (#) and/or ('Heading 1')
     *       2. If included  reduce the selected text by this elements and add replace the selected text by textToAdd
     *       3. If not included add (#) before the selected text and insert them into the editor
     *       4. Heading one in markdown language appears
     */
    execute(): void {
        let selectedText = this.getSelectedText();

        if (selectedText.includes('#') && !selectedText.includes('Heading 1')) {
            const textToAdd = selectedText.slice(2);
            this.insertText(textToAdd);
        } else if (selectedText.includes('#') && selectedText.includes('Heading 1')) {
            const textToAdd = selectedText.slice(2, -9);
            this.insertText(textToAdd);
        } else {
            const initText = 'Heading 1';
            const range = this.getRange();
            selectedText = `# ${selectedText || initText}`;
            this.replace(range, selectedText);
            this.focus();
        }
    }
}
