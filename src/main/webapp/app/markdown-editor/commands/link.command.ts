import { Command } from './command';

export class LinkCommand extends Command {

    buttonIcon = 'link';
    buttonTranslationString =  'arTeMiSApp.multipleChoiceQuestion.editor.link';

    /**
     * @function execute
     * @desc Add/Remove a link in markdown language
     *       1. check if the selected text includes ('[](http://)')
     *       2. If included reduce the selected text by this elements and replace the selected text by textToAdd
     *       3. If not included add ('[](http://)') at the cursor in the editor
     *       4. Link in markdown language appears
     */
    execute(): void {
        let selectedText = this.editor.getSelectedText();
        let textToAdd = '';

        if (selectedText.includes('[](http://)')) {
            textToAdd = selectedText.slice(10);
            this.editor.insert(textToAdd);
        } else {
            const range = this.editor.selection.getRange();
            selectedText = `[](http://)`;
            this.editor.session.replace(range, selectedText);
            this.editor.focus();
        }
    }
}
