import { SpecialCommand } from 'app/markdown-editor/specialCommands/specialCommand';

export class IncorrectOptionCommand extends SpecialCommand {
    buttonTitle = 'Incorrect Option';

    /**
     * @function addAnswerOptionTextToEditor
     * @desc Adds the markdown for a correct or incorrect answerOption at the end of the current markdown text
     * @param mode {boolean} mode true sets the text for an correct answerOption, false for an incorrect one
     */
    execute(editor: any): void {
        const addedText = '\n[x] Enter a correct answer option here';
        editor.focus();
        editor.clearSelection();
        editor.moveCursorTo(editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        editor.insert(addedText);
        const range = editor.selection.getRange();
        range.setStart(range.start.row, 6);
        editor.selection.setRange(range);
    }
}
