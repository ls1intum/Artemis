import { SpecialCommand } from 'app/markdown-editor/specialCommands/specialCommand';

export class ExplanationCommand extends SpecialCommand {
    buttonTitle = 'Explanation';

    execute(editor: any): void {
        const addedText = '\n\t[-e] Add an explanation here (only visible in feedback after quiz has ended)';
        editor.focus();
        editor.clearSelection();
        editor.moveCursorTo(editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        editor.insert(addedText);
        const range = editor.selection.getRange();
        range.setStart(range.start.row, 6);
        editor.selection.setRange(range);
    }
}
