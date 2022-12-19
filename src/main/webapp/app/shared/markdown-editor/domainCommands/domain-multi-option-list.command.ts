import { DomainMultiOptionCommand } from 'app/shared/markdown-editor/domainCommands/domainMultiOptionCommand';
import { getStringSegmentPositions } from 'app/shared/util/global.utils';
import { removeTextRange } from 'app/shared/util/markdown.util';

/**
 * Allows the insertion of values within a comma separated list.
 * Will e.g. remove duplicates and append new items to the list.
 */
export abstract class DomainMultiOptionListCommand extends DomainMultiOptionCommand {
    protected abstract getValueMeta(): string;

    setEditor(aceEditor: any) {
        super.setEditor(aceEditor);

        const autoCompleter = {
            getCompletions: (editor: any, session: any, pos: any, prefix: any, callback: any) => {
                callback(
                    null,
                    this.getValues().map(({ value, id }) => {
                        return {
                            caption: value,
                            value: id,
                            meta: this.getValueMeta(),
                        };
                    }),
                );
            },
        };

        this.addCompleter(autoCompleter);
    }

    /**
     * Update the list of values in the multi option list. Makes sure to not add duplicates into the brackets.
     *
     * @function execute
     * @desc insert selected value into text
     */
    execute(valueToAdd: string): void {
        const cursorPosition = this.getCursorPosition();
        const matchInTag = this.isCursorWithinTag();

        this.clearSelection();

        const newValuesList = this.generateValueList(matchInTag, valueToAdd, cursorPosition);
        const newValuesStringified = `${this.getOpeningIdentifier()}${newValuesList.join(',')}${this.getClosingIdentifier()}`;
        if (matchInTag) {
            removeTextRange(
                { col: matchInTag.matchStart, row: cursorPosition.row },
                {
                    col: matchInTag.matchEnd,
                    row: cursorPosition.row,
                },
                this.aceEditor,
            );
        }
        this.insertText(newValuesStringified);
        this.focus();
    }

    /**
     * Given a match, the new value to add and the cursor position, determine and return the updated value list.
     * a) The cursor is within the brackets -> add/replace value in list.
     * b) The cursor is NOT within the brackets -> just paste in a new value.
     *
     * @param match
     * @param valueToAdd
     * @param cursorPosition
     */
    private generateValueList = (
        match: { matchStart: number; matchEnd: number; innerTagContent: string } | null,
        valueToAdd: string,
        cursorPosition: { row: number; column: number },
    ): string[] => {
        const { column } = cursorPosition;
        // Check if the cursor is within the tag - if so, add the value to the list.
        // Also don't add a value again that is already included.
        if (match && !match.innerTagContent.includes(valueToAdd)) {
            const currentValues = match.innerTagContent.split(',');
            const stringPositions = getStringSegmentPositions(match.innerTagContent, ',');
            const wordUnderCursor = stringPositions.find(({ start, end }) => column - 1 - match.matchStart > start && column - 1 - match.matchStart < end);
            if (wordUnderCursor) {
                // Case 1: Replace value.
                return currentValues.map((val) => (val === wordUnderCursor.word ? valueToAdd : val));
            } else if (column >= match.matchEnd - 1) {
                // Case 2: Add value on left side.
                return [...currentValues, valueToAdd];
            } else if (column <= match.matchStart + 1) {
                // Case 3: Add value on right side.
                return [valueToAdd, ...currentValues];
            } else {
                // Case 4: Fallback - replace current.
                return [valueToAdd];
            }
        } else if (match && match.innerTagContent.includes(valueToAdd)) {
            // Case 5: The value is already included, do nothing.
            return match.innerTagContent.split(',');
        } else {
            // Case 6: There is no content yet, just paste the current value in.
            return [valueToAdd];
        }
    };
}
