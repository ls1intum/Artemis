import { Command } from './command';

export abstract class Heading extends Command {

    buttonIcon = 'heading';
    buttonTranslationString = 'arTeMiSApp.multipleChoiceQuestion.editor.heading';

    execute(): void {
    }
}
