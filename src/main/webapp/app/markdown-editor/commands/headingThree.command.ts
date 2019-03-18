import { Command } from 'app/markdown-editor/commands/command';

export class HeadingThreeCommand extends Command {

    buttonIcon = 'heading3';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.headingThree';

    execute(): void {
        let selectedText = this.editor.getSelectedText();
        let textToAdd = '';

            if (selectedText.includes('###') && !selectedText.includes('Heading 3')) {
                textToAdd = selectedText.slice(3);
                this.editor.insert(textToAdd);
            } else if (selectedText.includes('###') && selectedText.includes('Heading 3')) {
                textToAdd = selectedText.slice(3, -9);
                this.editor.insert(textToAdd);
            } else {
                const initText = 'Heading 3';
                const range = this.editor.selection.getRange();
                selectedText = `### ${selectedText || initText}`;
                this.editor.session.replace(range, selectedText);
                this.editor.focus();
        }
    }
}
