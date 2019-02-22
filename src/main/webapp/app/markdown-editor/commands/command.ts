export abstract class Command {

    buttonTitle: string;
    buttonIcon: string;

    abstract execute(editor: any): void;
}
