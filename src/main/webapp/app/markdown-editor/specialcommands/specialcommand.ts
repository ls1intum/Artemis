export abstract class Specialcommand {
    buttonTitle: string;

    abstract execute(editor: any): void;
}
