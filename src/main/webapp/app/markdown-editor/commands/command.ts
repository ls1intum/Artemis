export abstract class Command {

    buttonTranslationString: string;
    protected editor: any;

    public setEditor(editor: any): void {
        this.editor = editor;
    }

    abstract execute(): void;
}
