import { DomainMultiOptionCommand } from 'app/markdown-editor/domainCommands';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';

export class TestCaseCommand extends DomainMultiOptionCommand {
    buttonTranslationString = 'arTeMiSApp.programmingExercise.problemStatement.testCaseCommand';

    /**
     * @function execute
     * @desc insert selected testCase value into text
     */
    execute(value: string): void {
        const row = this.aceEditorContainer.getEditor().getCursorPosition().row;
        const text = `${this.getOpeningIdentifier()}${value}${this.getClosingIdentifier()}`;
        const matchInTag = this.isCursorWithinTag();

        // Check if the cursor is within the tag - if so, replace its content
        if (matchInTag) {
            this.aceEditorContainer.getEditor().moveCursorTo(row, matchInTag.matchStart);
            ArtemisMarkdown.removeTextRange({ col: matchInTag.matchStart, row }, { col: matchInTag.matchEnd, row }, this.aceEditorContainer);
            ArtemisMarkdown.addTextAtCursor(text, this.aceEditorContainer);
            this.focus();
            return;
        }

        // Check if there is an occurrence of the test case in the line of the cursor - if so, replace its content
        const matchInRow = this.isTagInRow(row);
        if (matchInRow) {
            this.aceEditorContainer.getEditor().moveCursorTo(row, matchInRow.matchStart);
            ArtemisMarkdown.removeTextRange(
                { col: matchInRow.matchStart, row },
                {
                    col: matchInRow.matchEnd,
                    row,
                },
                this.aceEditorContainer,
            );
            ArtemisMarkdown.addTextAtCursor(text, this.aceEditorContainer);
            this.focus();
            return;
        }

        ArtemisMarkdown.addTextAtCursor(text, this.aceEditorContainer);
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
