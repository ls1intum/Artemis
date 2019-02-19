import { Command } from './command';

export class BoldCommand extends Command {
    buttonTitle = 'Bold';

    execute(editor: any): void {
        let chosenText = editor.getSelectedText();
        let textToAdd = '';

        if (chosenText.includes('**')) {
            textToAdd = chosenText.slice(2, -2);
            editor.insert(textToAdd);
        } else {
            textToAdd = `**${chosenText}**`;
            editor.insert(textToAdd);
        }
    }
}
