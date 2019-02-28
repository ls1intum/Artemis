import { Command } from './command';

export class ItalicCommand extends Command {

    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.italic';

    execute(): void {
        const chosenText = this.editor.getSelectedText();
        let textToAdd = '';

        if (chosenText.includes('*')) {
            textToAdd = chosenText.slice(1, -1);
            this.editor.insert(textToAdd);
        } else {
            textToAdd = `*${chosenText}*`;
            this.editor.insert(textToAdd);
        }
    }
}
