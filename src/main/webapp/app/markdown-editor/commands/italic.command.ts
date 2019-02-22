import { Command } from './command';

export class ItalicCommand extends Command {

    buttonTitle = 'Italic';

    execute(editor: any): void {
        const chosenText = editor.getSelectedText();
        let textToAdd = '';

        if (chosenText.includes('*')) {
            textToAdd = chosenText.slice(1, -1);
            editor.insert(textToAdd);
        } else {
            textToAdd = `*${chosenText}*`;
            editor.insert(textToAdd);
        }
    }
}
