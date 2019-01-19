import { Ace } from 'ace-builds';
import { Specialcommand } from 'app/markdown-editor/specialcommands/specialcommand';

export class HintCommand extends Specialcommand {
    buttonTitle = 'Hint';

    execute(editor: any): void {
        const addedText = "\n\t[-h] Add a hint here (visible during the quiz via '?'-Button)";
        editor.focus();
        editor.clearSelection();
        editor.moveCursorTo(editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        editor.insert(addedText);
        const range = editor.selection.getRange();
        range.setStart(range.start.row, 6);
        editor.selection.setRange(range);
    }
}
