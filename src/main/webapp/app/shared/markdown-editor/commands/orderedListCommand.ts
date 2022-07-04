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
        const extendedText = this.getExtendedSelectedText();
        this.handleManipulation(extendedText);
    }

    /**
     * Performs the necessary manipulations.
     * @param extendedText the extended text
     */
    handleManipulation(extendedText: string[]): void {
        let manipulatedText = '';
        let position = 1;

        extendedText.forEach((line, index) => {
            // Special case: Single empty line
            if (line === '') {
                if (extendedText.length === 1) {
                    manipulatedText = '1. ';
                    return;
                }
            } else {
                // Manipulate the line, e.g. remove the number or add the number.
                manipulatedText += this.manipulateLine(line, position);
                position++;
            }

            if (index !== extendedText.length - 1) {
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
                // Add whitespaces and remove first whitespace after the dot
                return ' '.repeat(whitespaces) + elements[1].substring(1);
            }
        }

        // Add the position of the list element
        return ' '.repeat(whitespaces) + `${position}. ${line.substring(whitespaces)}`;
    }
}
