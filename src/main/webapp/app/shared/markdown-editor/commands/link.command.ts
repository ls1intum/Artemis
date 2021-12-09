import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faLink } from '@fortawesome/free-solid-svg-icons';
import { Command } from './command';

export class LinkCommand extends Command {
    buttonIcon = faLink as IconProp;
    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.link';

    /**
     * @function execute
     * @desc Add/Remove a link in markdown language
     *       1. check if the selected text includes ('[](http://)')
     *       2. If included reduce the selected text by this elements and replace the selected text by textToAdd
     *       3. If not included add ('[](http://)') at the cursor in the editor
     *       4. Link in markdown language appears
     */
    execute(): void {
        let selectedText = this.getSelectedText();

        if (selectedText.includes('[](http://)')) {
            const textToAdd = selectedText.slice(10);
            this.insertText(textToAdd);
        } else {
            const range = this.getRange();
            selectedText = `[](http://)`;
            this.replace(range, selectedText);
            this.focus();
        }
    }
}
