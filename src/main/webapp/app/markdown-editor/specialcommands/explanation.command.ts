import { Ace } from 'ace-builds';
import { Specialcommand } from 'app/markdown-editor/specialcommands/specialcommand';

export class ExplanationCommand extends Specialcommand {
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
