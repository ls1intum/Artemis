import { AceEditorComponent } from 'ng2-ace-editor';
import { DomainMultiOptionCommand } from 'app/markdown-editor/domainCommands/domainMultiOptionCommand';
import { getStringSegmentPositions } from 'app/utils/global.utils';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';

/**
 * Allows the insertion of values within a comma separated list.
 * Will e.g. remove duplicates and append new items to the list.
 */
export abstract class DomainMultiOptionListCommand extends DomainMultiOptionCommand {
    protected abstract getValueMeta(): string;

    setEditor(aceEditorContainer: AceEditorComponent) {
        super.setEditor(aceEditorContainer);

        const testCaseCompleter = {
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

        this.aceEditorContainer.getEditor().completers = [...this.aceEditorContainer.getEditor().completers, testCaseCompleter];
    }

    /**
     * There are two base cases for this command:
     * a) The cursor is within the brackets -> add/replace test case in test case list.
     * b) The cursor is NOT within the brackets -> just paste in a new test case command.
     * @function execute
     * @desc insert selected testCase value into text
     */
    execute(value: string): void {
        const { row, column } = this.getCursorPosition();
        const matchInTag = this.isCursorWithinTag()!;

        const generateTestCases = (match: { matchStart: number; matchEnd: number; innerTagContent: string }): string[] => {
            // Check if the cursor is within the tag - if so, add the test to the list.
            // Also don't add a test case again that is already included.
            if (match && !match.innerTagContent.includes(value)) {
                const currentValues = matchInTag.innerTagContent.split(',');
                const stringPositions = getStringSegmentPositions(match.innerTagContent, ',');
                const wordUnderCursor = stringPositions.find(({ start, end }) => column - 1 - match.matchStart > start && column - 1 - match.matchStart < end);
                if (wordUnderCursor) {
                    // Case 1: Replace test.
                    return currentValues.map(test => (test === wordUnderCursor.word ? value : test));
                } else if (column >= match.matchEnd - 1) {
                    // Case 2: Add test on left side.
                    return [...currentValues, value];
                } else if (column <= match.matchStart + 1) {
                    // Case 3: Add test on right side.
                    return [value, ...currentValues];
                } else {
                    // Case 4: Fallback - replace current.
                    return [value];
                }
            } else if (match && match.innerTagContent.includes(value)) {
                // Case 5: The test case is already included, do nothing.
                return matchInTag.innerTagContent.split(',');
            } else {
                // Case 6: There is no content yet, just paste the current value in.
                return [value];
            }
        };

        this.clearSelection();
        const newTestCases = generateTestCases(matchInTag);
        const newTestCasesStringified = `${this.getOpeningIdentifier()}${newTestCases.join(',')}${this.getClosingIdentifier()}`;
        if (matchInTag) {
            ArtemisMarkdown.removeTextRange(
                { col: matchInTag.matchStart, row },
                {
                    col: matchInTag.matchEnd,
                    row,
                },
                this.aceEditorContainer,
            );
        }
        this.insertText(newTestCasesStringified);
        this.focus();
    }
}
