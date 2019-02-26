import {Command} from 'app/markdown-editor/commands/command';
import {Question} from 'app/entities/question';

export abstract class SpecialCommand extends Command {
    identifier: string;

    public question: Question;

    public setQuestion(question: Question) {
        this.question = question;
    }
}
