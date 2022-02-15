import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faHeading } from '@fortawesome/free-solid-svg-icons';
import { Command } from 'app/shared/markdown-editor/commands/command';

export class HeadingThreeCommand extends Command {
    buttonIcon = faHeading as IconProp;
    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.headingThree';

    /**
     * @function execute
     * @desc Create/Remove heading three language
     *       1. check if the selected text includes (###) and/or ('Heading 3')
     *       2. If included reduce the selected text by this elements and add replace the selected text by textToAdd
     *       3. If not included  add (###) before the selected text and insert them into the editor
     *       4. Heading three in markdown language appears
     */
    execute(): void {
        let selectedText = this.getSelectedText();

        if (selectedText.includes('###') && !selectedText.includes('Heading 3')) {
            const textToAdd = selectedText.slice(4);
            this.insertText(textToAdd);
        } else if (selectedText.includes('###') && selectedText.includes('Heading 3')) {
            const textToAdd = selectedText.slice(4, -9);
            this.insertText(textToAdd);
        } else {
            const initText = 'Heading 3';
            const range = this.getRange();
            selectedText = `### ${selectedText || initText}`;
            this.replace(range, selectedText);
            this.focus();
        }
    }
}
