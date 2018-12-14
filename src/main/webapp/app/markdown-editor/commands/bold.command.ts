import { Command } from './command';

export class BoldCommand extends Command {
    buttonTitle = 'Bold';
    buttonIcon = 'fa fa-bold';

    execute(editor: any): void {
        let chosenText = editor.getSelectedText();
        editor.remove('left');
        editor.clearSelection();
        const textToAdd = `**${chosenText}**`;
        editor.moveCursorTo(editor.getCursorPosition().row, Number.POSITIVE_INFINITY);
        //const search = new Search();
        //search.set({needle: chosenText});
        //editor.replace(textToAdd, search);
        editor.insert(textToAdd);
    }
}
