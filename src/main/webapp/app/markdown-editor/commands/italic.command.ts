import { Command } from './command';

export class ItalicCommand extends Command {
    buttonTitle = 'Italic';
    buttonIcon = 'fa fa-italic';

    execute(editor: any): void {
        let chosenText = editor.getSelectedText();
        editor.remove('left');
        editor.clearSelection();
        const textToAdd = `*${chosenText}*`;
        editor.moveCursorTo(editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        //const search = new Search();
        //search.set({needle: chosenText});
        //editor.replace(textToAdd, search);
        editor.insert(textToAdd);
    }
}
