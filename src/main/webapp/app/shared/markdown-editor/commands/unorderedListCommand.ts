import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faListUl } from '@fortawesome/free-solid-svg-icons';
import { Command } from './command';

export class UnorderedListCommand extends Command {
    buttonIcon = faListUl as IconProp;
    buttonTranslationString = 'artemisApp.multipleChoiceQuestion.editor.unorderedList';

    /**
     * @function execute
     * @desc Use the Markdown language for creating an unordered list
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

        extendedText.forEach((line, index) => {
            // Special case: Single empty line
            if (line === '') {
                if (extendedText.length === 1) {
                    manipulatedText = '- ';
                    return;
                }
            } else {
                manipulatedText += this.manipulateLine(line);
            }

            if (index !== extendedText.length - 1) {
                manipulatedText += '\n';
            }
        });
        this.replace(this.getRange(), manipulatedText);
    }

    /**
     * Manipulates a given line and adds or removes the - at the beginning.
     * @param line to manipulate
     * @return manipulated line
     */
    manipulateLine(line: string): string {
        const index = line.indexOf('-');

        // Calc leading whitespaces
        const whitespaces = line.search(/\S|$/);

        // There is - in this line
        if (index !== -1) {
            const elements = [line.slice(whitespaces, index), line.slice(index + 1)];

            // Check if this is the first -
            if (elements[0] === '' && elements[1].length >= 1 && elements[1].startsWith(' ')) {
                // Add whitespaces and remove first whitespace after the dot
                return ' '.repeat(whitespaces) + elements[1].substring(1);
            }
        }

        // Add the -
        return ' '.repeat(whitespaces) + `- ${line.substring(whitespaces)}`;
    }
}
