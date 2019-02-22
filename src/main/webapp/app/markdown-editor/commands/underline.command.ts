import { Command } from './command';

export class UnderlineCommand extends Command {

    buttonTitle = 'Underline';

    execute(editor: any): void {
        const chosenText = editor.getSelectedText();
        let textToAdd = '';

        if (chosenText.includes('<ins>')) {
            textToAdd = chosenText.slice(5, -6);
            editor.insert(textToAdd);
        } else {
            textToAdd = `<ins>${chosenText}</ins>`;
            editor.insert(textToAdd);
        }
    }
}
