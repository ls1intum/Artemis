import { Command } from 'app/markdown-editor/commands/command';

export class HeadingOneCommand extends Command {

    buttonIcon = 'heading1';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.headingOne';

    execute(): void {
        let selectedText = this.editor.getSelectedText();
        let textToAdd = '';

        if (selectedText.includes('#')) {
            textToAdd = selectedText.slice(3);
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
