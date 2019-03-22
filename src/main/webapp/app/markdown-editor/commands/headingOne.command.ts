import { Command } from 'app/markdown-editor/commands/command';

export class HeadingOneCommand extends Command {

    buttonIcon = 'heading1';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.headingOne';

    /**
     * @function execute
     * @desc Create/remove heading one in markdown language
     *       1. Check if the selected text includes (#) and/or ('Heading 1')
     *       2. If included  reduce the selected text by this elements and add replace the selected text by textToAdd
     *       3. If not included add (#) before the selected text and insert them into the editor
     *       4. Heading one in markdown language appears
     */
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
