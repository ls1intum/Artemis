import { Command } from './command';

export class BoldCommand extends Command {

        buttonIcon =  'bold';
        buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.bold';

    execute(): void {
        const chosenText = this.editor.getSelectedText();
        let textToAdd = '';

        if (chosenText.includes('**')) {
            textToAdd = chosenText.slice(2, -2);
            this.editor.insert(textToAdd);
        } else {
            textToAdd = `**${chosenText}**`;
            this.editor.insert(textToAdd);
        }
    }
}
