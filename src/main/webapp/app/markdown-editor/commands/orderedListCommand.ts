import { Command } from './command';

export class OrderedListCommand extends Command {

    buttonIcon = 'list-ol';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.orderedList';

    /**
     * @function execute
     * @desc Use the markdown language for creating/removing an ordered list
     */
    execute(): void {
        const selectedText = this.getSelectedText();
        this.splitText(selectedText);
    }

    /**
     * @function splitText
     * @desc 1. Split the text at the line break into an array
     *       2. Assign each line the position it has in the array
     *       3. Call for each textline the replaceText method
     * @param {string} the selected text by the cursor
     */
    splitText(selectedText: string): void {
        const parseArray = selectedText.split('\n');
        let addAmount = parseArray.length - 1;
        for (const element of parseArray) {
             this.replaceText(element, parseArray.length - addAmount);
             addAmount--;
        }
    }

    /**
     * @function execute
     * @desc 1. Check if the selected text includes (.)
     *       2. If included reduce the selected text by this element and replace the selected text by textToAdd
     *       3. If not included place the position before the selected text and add them to the editor
     *       4. An ordered list in markdown language appears
     * @param {string} with the position {number} it has in the array
     */
    replaceText(element: string, position: number): void {
        if (element.includes('.')) {
            const textToAdd = element.slice(3);
            const text = `${textToAdd}\n`;
            this.insertText(text);
        } else if (element === '') {
            const range = this.getRange();
            element = `1. ${element}`;
            this.replace(range, element);
            this.focus();
        } else {
            element = `${position}. ${element}\n`;
            this.insertText(element);
        }
    }
}
