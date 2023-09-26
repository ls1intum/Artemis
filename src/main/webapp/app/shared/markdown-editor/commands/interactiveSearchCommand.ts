import { HttpResponse } from '@angular/common/http';
import { MultiOptionCommand } from 'app/shared/markdown-editor/commands/multiOptionCommand';
import { Observable } from 'rxjs';
import { SelectWithSearchComponent } from 'app/shared/markdown-editor/select-with-search/select-with-search.component';

export abstract class InteractiveSearchCommand extends MultiOptionCommand {
    private insertedAssociatedCharacter = false;
    private selectWithSearchComponent: SelectWithSearchComponent;
    execute(): void {}

    setEditor(aceEditor: any) {
        super.setEditor(aceEditor);

        this.aceEditor.commands.addCommand({
            name: this.getAssociatedInputCharacter(),
            bindKey: { win: this.getAssociatedInputCharacter(), mac: this.getAssociatedInputCharacter() },
            exec: (editor: any) => {
                this.insertedAssociatedCharacter = true;
                editor.insert(this.getAssociatedInputCharacter());
                this.selectWithSearchComponent?.open();
            },
        } as any);
    }

    setSelectWithSearchComponent(component: SelectWithSearchComponent) {
        this.selectWithSearchComponent = component;
    }

    insertSelection(selected: any) {
        if (selected !== undefined) {
            const cursorPosition = this.aceEditor.getCursorPosition();

            if (this.insertedAssociatedCharacter) {
                this.aceEditor.session.getDocument().removeInLine(cursorPosition.row, cursorPosition.column - 1, cursorPosition.column);
                this.insertedAssociatedCharacter = false;
            }

            this.insertText(this.selectionToText(selected));
        }

        this.aceEditor.focus();
    }

    abstract performSearch(searchTerm: string): Observable<HttpResponse<any[]>>;

    protected abstract selectionToText(selected: any): string;

    protected abstract getAssociatedInputCharacter(): string;
}
