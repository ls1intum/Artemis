/** abstract class for all commands - default and domain commands of ArTEMiS
 *  default commands: markdown commands e.g. bold, italic
 *  domain commands: ArTEMiS customized commands **/
export abstract class Command {

    buttonIcon: string;
    buttonTranslationString: string;
    protected editor: any;

    public setEditor(editor: any): void {
        this.editor = editor;
    }

    abstract execute(): void;
}
