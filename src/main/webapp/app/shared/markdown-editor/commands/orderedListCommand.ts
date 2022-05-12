import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faListOl } from '@fortawesome/free-solid-svg-icons';
import { Command } from './command';

export class OrderedListCommand extends Command {
    buttonIcon = faListOl as IconProp;
    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.orderedList';

    /**
     * @function execute
     * @desc Use the Markdown language for creating/removing an ordered list
     */
    execute(): void {
        const selectedText = this.getSelectedText();
        this.splitText(selectedText);
    }

    /**
     * Splits the text and performs the necessary manipulations.
     * @function splitText
     * @param selectedText the selected text by the cursor
     */
    splitText(selectedText: string): void {
        const parseArray = selectedText.split('\n');
        let manipulatedText = '';
        let position = 1;

        parseArray.forEach((line, index) => {
            // Empty line -> we start again
            if (line === '') {
                // Special case: Single empty line
                if (parseArray.length === 1) {
                    manipulatedText = '1. ';
                    return;
                }
                position = 1;
            } else {
                // Manipulate the line, e.g. remove the number or add the number.
                manipulatedText += this.manipulateLine(line, position);
                position++;
            }

            if (index !== parseArray.length - 1) {
                manipulatedText += '\n';
            }
        });
        this.replace(this.getRange(), manipulatedText);
    }

    /**
     * Manipulates a given line and adds or removes the numbers at the beginning.
     * @param line to manipulate
     * @param position of the line in the ordered list
     * @return manipulated line
     */
    manipulateLine(line: string, position: number): string {
        const index = line.indexOf('.');

        // Calc leading whitespaces
        const whitespaces = line.search(/\S|$/);

        // There is a dot in this line
        if (index !== -1) {
            const elements = [line.slice(whitespaces, index), line.slice(index + 1)];

            // Check if this is really a number
            if (elements[0] !== '' && !isNaN(Number(elements[0]))) {
                // Remove first whitespace
                return ' '.repeat(whitespaces) + elements[1].substring(1);
            }
        }

        // Add the position of the list element
        return ' '.repeat(whitespaces) + `${position}. ${line.substring(whitespaces)}`;
    }
}
