import { Command } from './command';

export class UnorderedListCommand extends Command {

    buttonIcon = 'list-ul';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.unorderedList';

    /**
     * @function execute
     * @desc Use the markdown language for creating an unordered list
     */
    execute(): void {
        const selectedText = this.getSelectedText();
        this.splitText(selectedText);
    }

    /**
     * @function splitText
     * @desc 1. Split the text at the line break into an array
     *       2. Call for each textline the replaceText method
     * @param {string} the selected text by the cursor
     */
    splitText(selectedText: string): void {
        const parseArray = selectedText.split('\n');
        parseArray.forEach( element => this.replaceText(element));
    }

    /**
     * @function replaceText
     * @desc 1. Check if the selected text includes (-)
     *       2. If included reduce the selected text by 2 (-, whitespace) and replace the selected text by textToAdd
     *       3. If not included combine (-) with the selected text and insert into the editor
     *       4. An unordered list in markdown appears
     * @param element {string} extracted textLine from the {array} selectedText
     */
    replaceText(element: string): void {
        if (element.includes('-')) {
            const textToAdd = element.slice(2);
            const text = `${textToAdd}\n`;
            this.insertText(text);
        } else if (element === '') {
            const range = this.getRange();
            element = `- ${element}`;
            this.replace(range, element);
            this.focus();
        } else {
            const range = this.getRange();
            element = `- ${element}\n`;
            this.replace(range, element);
            this.focus();
        }
    }
}
