import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faImage } from '@fortawesome/free-solid-svg-icons';
import { Command } from './command';

export class AttachmentCommand extends Command {
    buttonIcon = faImage as IconProp;
    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.imageUpload';

    /**
     * @function execute
     * @desc insert/remove the markdown command for uploading an attachment
     *       1. Check if the selected text includes ('![](http://)')
     *       2. If included reduce the selected text by this elements and replace the selected text by textToAdd
     *       3. If not included add ('![](http://)') at the cursor position in the editor
     *       4. Attachment in Markdown language appears
     */
    execute(): void {
        let selectedText = this.getSelectedText();

        if (selectedText.includes('![](http://)')) {
            const textToAdd = selectedText.slice(12);
            this.insertText(textToAdd);
        } else {
            const range = this.getRange();
            selectedText = `![](http://)`;
            this.replace(range, selectedText);
            this.focus();
        }
    }
}
