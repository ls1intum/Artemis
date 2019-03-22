import { Command } from './command';

export class AttachmentCommand extends Command {

    buttonIcon = 'image';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.imageUpload';

    /**
     * @function execute
     * @desc insert/remove the markdown command for uploading an attachment
     *       1. Check if the selected text includes ('![](http://)')
     *       2. If included reduce the selected text by this elements and replace the selected text by textToAdd
     *       3. If not included add ('![](http://)') at the cursor position in the editor
     *       4. Attachment in markdown language appears
     */
    execute(): void {
        let selectedText = this.editor.getSelectedText();
        let textToAdd = '';

        if (selectedText.includes('![](http://)')) {
            textToAdd = selectedText.slice(10);
            this.editor.insert(textToAdd);
        } else {
            const range = this.editor.selection.getRange();
            selectedText = `![](http://)`;
            this.editor.session.replace(range, selectedText);
            this.editor.focus();
        }
    }
}
