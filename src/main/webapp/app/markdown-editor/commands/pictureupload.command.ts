import { Command } from './command';

export class PictureuploadCommand extends Command {

    buttonIcon = 'image';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.imageUpload';

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
