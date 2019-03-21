import { SpecialCommand } from 'app/markdown-editor/specialCommands/specialCommand';

export class ExplanationCommand extends SpecialCommand {

    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.addExplanation';

    execute(): void {
        const addedText = '\n\t[-e] Add an explanation here (only visible in feedback after quiz has ended)';
        this.editor.focus();
        this.editor.clearSelection();
        this.editor.moveCursorTo(this.editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        this.editor.insert(addedText);
        const range = this.editor.selection.getRange();
        range.setStart(range.start.row, 6);
        this.editor.selection.setRange(range);
    }

    getIdentifier(): string {
        return 'e]';
    }
}
