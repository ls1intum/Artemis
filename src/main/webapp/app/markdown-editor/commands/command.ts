export abstract class Command {

    buttonIcon: string;
    buttonTranslationString: string;
    protected editor: any;

    public setEditor(editor: any): void {
        this.editor = editor;
    }

    abstract execute(): void;
}
