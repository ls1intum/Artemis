import { Command } from 'app/markdown-editor/commands/command';

export class HeadingOneCommand extends Command {

    buttonIcon = 'heading1';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.headingOne';

    execute(): void {
        let selectedText = this.editor.getSelectedText();
        let textToAdd = '';

        if (selectedText.includes('#') && !selectedText.includes('Heading 1')) {
            textToAdd = selectedText.slice(2);
            this.editor.insert(textToAdd);
        } else if (selectedText.includes('#') && selectedText.includes('Heading 1')) {
            textToAdd = selectedText.slice(2, -9);
            this.editor.insert(textToAdd);
        } else {
            const initText = 'Heading 1';
            const range = this.editor.selection.getRange();
            selectedText = `# ${selectedText || initText}`;
            this.editor.session.replace(range, selectedText);
            this.editor.focus();
        }
    }
}
