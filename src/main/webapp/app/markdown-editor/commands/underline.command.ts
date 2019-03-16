import { Command } from './command';

export class UnderlineCommand extends Command {

    buttonIcon = 'underline';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.underline';

    execute(): void {
        const chosenText = this.editor.getSelectedText();
        let textToAdd = '';

        if (chosenText.includes('<ins>')) {
            textToAdd = chosenText.slice(5, -6);
            this.editor.insert(textToAdd);
        } else {
            textToAdd = `<ins>${chosenText}</ins>`;
            this.editor.insert(textToAdd);
        }
    }
}
