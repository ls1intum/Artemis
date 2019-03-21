import { SpecialCommand } from 'app/markdown-editor/specialCommands/specialCommand';

export class HintCommand extends SpecialCommand {

    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.addHint';

    execute(): void {
        const addedText = "\n\t[-h] Add a hint here (visible during the quiz via '?'-Button)";
        this.editor.focus();
        this.editor.clearSelection();
        this.editor.moveCursorTo(this.editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        this.editor.insert(addedText);
        const range = this.editor.selection.getRange();
        range.setStart(range.start.row, 6);
        this.editor.selection.setRange(range);
    }

    getIdentifier(): string {
        return 'h]';
    }
}
