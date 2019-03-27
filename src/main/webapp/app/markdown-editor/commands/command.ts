import { AceEditorComponent } from 'ng2-ace-editor';

/**
 * abstract class for all commands - default and domain commands of ArTEMiS
 * default commands: markdown commands e.g. bold, italic
 * domain commands: ArTEMiS customized commands
 */
export abstract class Command {

    buttonIcon: string;
    buttonTranslationString: string;
    protected aceEditorContainer: AceEditorComponent;

    public setEditor(aceEditorContainer: AceEditorComponent): void {
        this.aceEditorContainer = aceEditorContainer;
    }

    protected getSelectedText(): string {
        return this.aceEditorContainer.getEditor().getSelectedText();
    }

    protected insertText(text: string) {
        this.aceEditorContainer.getEditor().insert(text);
    }

    protected focus() {
        this.aceEditorContainer.getEditor().focus();
    }

    protected getRange(): Range {
        return this.aceEditorContainer.getEditor().selection.getRange();
    }

    protected replace(range: Range, text: string) {
        this.aceEditorContainer.getEditor().session.replace(range, text);
    }

    abstract execute(): void;
}
