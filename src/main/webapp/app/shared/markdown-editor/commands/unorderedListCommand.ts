import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faListUl } from '@fortawesome/free-solid-svg-icons';
import { Command } from './command';

export class UnorderedListCommand extends Command {
    buttonIcon = faListUl as IconProp;
    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.unorderedList';

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
        parseArray.forEach((element) => this.replaceText(element));
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
        /** case 1: text is formed in as an unordered list and the list should be unformed by deleting (-) + whitespace */
        if (element.includes('-')) {
            const textToAdd = element.slice(2);
            const text = `${textToAdd}\n`;
            this.insertText(text);
            /** case 2: start a new unordered list from scratch  */
        } else if (element === '') {
            const range = this.getRange();
            element = `- ${element}`;
            this.replace(range, element);
            this.focus();
        } else {
            /** case 3: formate existing text into an unformed list */
            const range = this.getRange();
            element = `- ${element}\n`;
            this.replace(range, element);
            this.focus();
        }
    }
}
