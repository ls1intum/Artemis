import { Command } from './command';
import { Ace } from 'ace-builds';
import SearchOptions = Ace.SearchOptions;
import Range = Ace.Range


export class BoldCommand extends Command {
    buttonTitle = 'Bold';

    execute(editor: any): void {
        let chosenText = editor.getSelectedText();
        let textToAdd = '';

        const range = editor.selection.getRange();

        if (chosenText.includes('**')) {
           textToAdd = chosenText.slice(2, -2);
           editor.insert(textToAdd);
        } else textToAdd = `**${chosenText}**`;

            editor.clearSelection();
            editor.moveCursorTo(editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
            const search: SearchOptions = {
                needle: chosenText,
                preventScroll: true,
                backwards: true,
                start: null,
                skipCurrent: false,
                range: range,
                preserveCase: true,
                regExp: chosenText,
                wholeWord: null,
                caseSensitive: true,
                wrap: false
            };

            editor.replace(textToAdd, search);
    }
}
