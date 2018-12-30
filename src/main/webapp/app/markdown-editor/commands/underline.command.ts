import { Command } from './command';

export class UnderlineCommand extends Command {
    buttonTitle = 'Underline';
    buttonIcon = 'fa fa-underline';

    execute(editor: any): void {
        let chosenText = editor.getSelectedText();
        editor.remove('left');
        editor.clearSelection();
        const textToAdd = `<ins>${chosenText}</ins>`;
        editor.moveCursorTo(editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        //const search = new Search();
        //search.set({needle: chosenText});
        //editor.replace(textToAdd, search);
        editor.insert(textToAdd);
    }
}
