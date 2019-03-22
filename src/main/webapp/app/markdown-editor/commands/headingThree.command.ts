import { Command } from 'app/markdown-editor/commands/command';

export class HeadingThreeCommand extends Command {

    buttonIcon = 'heading3';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.headingThree';

    /**
     * @function execute
     * @desc Create/Remove heading three language
     *       1. check if the selected text includes (###) and/or ('Heading 3')
     *       2. If included reduce the selected text by this elements and add replace the selected text by textToAdd
     *       3. If not included  add (###) before the selected text and insert them into the editor
     *       4. Heading three in markdown language appears
     */
    execute(): void {
        let selectedText = this.editor.getSelectedText();
        let textToAdd = '';

            if (selectedText.includes('###') && !selectedText.includes('Heading 3')) {
                textToAdd = selectedText.slice(4);
                this.editor.insert(textToAdd);
            } else if (selectedText.includes('###') && selectedText.includes('Heading 3')) {
                textToAdd = selectedText.slice(4, -9);
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
