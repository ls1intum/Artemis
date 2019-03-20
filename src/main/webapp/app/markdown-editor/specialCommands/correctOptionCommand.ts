import { SpecialCommand } from 'app/markdown-editor/specialCommands/specialCommand';

export class CorrectOptionCommand extends SpecialCommand {

    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.addCorrectAnswerOption';

    /**
     * @function add a new correct answer option to the text editor
     */
    execute(): void {
        const addedText = '\n[x] Enter a correct answer option here';
        this.editor.focus();
        this.editor.clearSelection();
        this.editor.moveCursorTo(this.editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        this.editor.insert(addedText);
        const range = this.editor.selection.getRange();
        range.setStart(range.start.row, 6);
        this.editor.selection.setRange(range);
    }

    getIdentifier(): string {
        return 'X]';
    }
}
