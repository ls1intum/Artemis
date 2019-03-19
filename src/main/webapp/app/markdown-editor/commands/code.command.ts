import { Command } from './command';

export class CodeCommand extends Command {

    buttonIcon =  'code';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.code';

    execute(): void {
        let selectedText = this.editor.getSelectedText();
        let textToAdd = '';

        if (selectedText.includes('```language ') && !selectedText.includes('Source Code')) {
            textToAdd = selectedText.slice(12, -3);
            this.editor.insert(textToAdd);
        } else if (selectedText.includes('```language ') && selectedText.includes('Source Code') ) {
            textToAdd = selectedText.slice(23, -3);
            this.editor.insert(textToAdd);
        } else {
            const range = this.editor.selection.getRange();
            const initText = 'Source Code';
            selectedText = '```language ' + (selectedText || initText) + '```';
            this.editor.session.replace(range, selectedText);
            this.editor.focus();
        }
    }
}
