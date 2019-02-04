import { Command } from './command';
import { Ace } from 'ace-builds';
import SearchOptions = Ace.SearchOptions;

export class UnderlineCommand extends Command {
    buttonTitle = 'Underline';


    execute(editor: any): void {
        let chosenText = editor.getSelectedText();

        let textToAdd = '';
        const range = editor.selection.getRange();

        if (chosenText.includes('<ins>')) {
            textToAdd = chosenText.slice(5, -6);
        } else textToAdd = `<ins>${chosenText}</ins>`;

        editor.clearSelection();
        editor.moveCursorTo(editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        const search: SearchOptions = {
            needle: chosenText,
            preventScroll: true,
            backwards: true,
            start: null,
            skipCurrent: false,
            range: range,
            preserveCase: false,
            regExp: chosenText,
            wholeWord: chosenText,
            caseSensitive: false,
            wrap: false
        };
        editor.replace(textToAdd, search);
    }
}
