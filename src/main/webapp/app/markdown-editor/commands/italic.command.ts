import { Command } from './command';
import { Ace } from 'ace-builds';
import SearchOptions = Ace.SearchOptions;

export class ItalicCommand extends Command {
    buttonTitle = 'Italic';

    execute(editor: any): void {
        let chosenText = editor.getSelectedText();
        let textToAdd = '';

        if (chosenText.includes('*')) {
            textToAdd = chosenText.slice(1, -1);
        } else textToAdd = `*${chosenText}*`;

        editor.clearSelection();
        editor.moveCursorTo(editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        const search: SearchOptions = {
            needle: chosenText,
            preventScroll: true,
            backwards: true,
            start: null,
            skipCurrent: false,
            range: null,
            preserveCase: false,
            regExp: chosenText,
            wholeWord: null,
            caseSensitive: false,
            wrap: false
        };
        editor.replace(textToAdd, search);
    }
}
