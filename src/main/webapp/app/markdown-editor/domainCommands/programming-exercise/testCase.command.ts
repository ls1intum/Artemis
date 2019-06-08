import { DomainMultiOptionCommand } from 'app/markdown-editor/domainCommands';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';

export class TestCaseCommand extends DomainMultiOptionCommand {
    buttonTranslationString = 'arTeMiSApp.programmingExercise.problemStatement.testCaseCommand';

    /**
     * @function execute
     * @desc insert selected testCase value into text
     */
    execute(value: string): void {
        const { row, column } = this.aceEditorContainer.getEditor().getCursorPosition();
        const text = `${this.getOpeningIdentifier()}${value}${this.getClosingIdentifier()}`;
        const matchInTag = this.isCursorWithinTag();

        // TODO: refactor
        const stringReducer = (x: string, acc: Array<{ start: number; end: number; word: string }> = []): Array<{ start: number; end: number; word: string }> => {
            const nextComma = x.indexOf(', ');
            if (nextComma === -1) {
                const lastElement = acc.length ? acc[acc.length - 1] : null;
                return [...acc, { start: lastElement ? lastElement.end + 2 : 0, end: ((lastElement && lastElement.end) || 0) + x.length - 1, word: x }];
            }
            const nextWord = x.slice(0, nextComma);
            const lastElement = acc.length ? acc[acc.length - 1] : null;
            const newAcc = [...acc, { start: lastElement ? lastElement.end + 2 : 0, end: ((lastElement && lastElement.end + 2) || 0) + nextComma - 2, word: nextWord }];
            const rest = x.slice(nextComma + 2);
            return stringReducer(rest, newAcc);
        };

        const generateTestCases = (match: { matchStart: number; matchEnd: number; innerTagContent: string }): string[] => {
            // Check if the cursor is within the tag - if so, add the test to the list
            if (matchInTag) {
                // Don't add a test case that is already included
                if (match.innerTagContent.includes(value)) return;

                this.aceEditorContainer.getEditor().clearSelection();
                const validTestCases = matchInTag.innerTagContent.split(', ').filter(test => this.getValues().includes(test));

                const stringPositions = stringReducer(match.innerTagContent);
                const wordUnderCursor = stringPositions.find(({ start, end }) => column - 1 - match.matchStart > start && column - 1 - match.matchStart < end);
                if (wordUnderCursor && validTestCases.includes(wordUnderCursor.word)) {
                    // Case 1: Replace test
                    return validTestCases.map(test => (test === wordUnderCursor.word ? value : test));
                } else if (column === match.matchEnd - 1) {
                    // Case 2: Add test on left side
                    return [...validTestCases, value];
                } else if (column === match.matchStart + 1) {
                    // Case 3: Add test on right side
                    return [value, ...validTestCases];
                } else {
                    return [value];
                }
            } else {
                return [value];
            }
        };

        this.aceEditorContainer.getEditor().clearSelection();
        const newTestCases = `${this.getOpeningIdentifier()}${sortBy(generateTestCases(matchInTag)).join(', ')}${this.getClosingIdentifier()}`;
        ArtemisMarkdown.removeTextRange(
            { col: matchInTag.matchStart, row },
            {
                col: matchInTag.matchEnd,
                row,
            },
            this.aceEditorContainer,
        );
        this.insertText(newTestCases);
        this.focus();
    }

    /**
     * @function getOpeningIdentifier
     * @desc identify the start of the task
     */
    getOpeningIdentifier(): string {
        return '(';
    }

    /**
     * @function getClosingIdentifier
     * @desc identify the end of the explanation
     */
    getClosingIdentifier(): string {
        return ')';
    }
}
