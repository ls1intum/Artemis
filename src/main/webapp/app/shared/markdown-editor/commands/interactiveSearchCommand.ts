import { HttpResponse } from '@angular/common/http';
import { MultiOptionCommand } from 'app/shared/markdown-editor/commands/multiOptionCommand';
import { Observable } from 'rxjs';
import { SelectWithSearchComponent } from 'app/shared/markdown-editor/select-with-search/select-with-search.component';

export interface SelectableItem {
    name?: string;
}

export abstract class InteractiveSearchCommand extends MultiOptionCommand {
    private selectWithSearchComponent: SelectWithSearchComponent;
    execute(): void {
        this.aceEditor.execCommand(this.getAssociatedInputCharacter());
    }

    private searchPositionStart: { row: number; column: number } | undefined;

    setEditor(aceEditor: any) {
        super.setEditor(aceEditor);

        this.aceEditor.commands.addCommand({
            name: this.getAssociatedInputCharacter(),
            bindKey: { win: this.getAssociatedInputCharacter(), mac: this.getAssociatedInputCharacter() },
            exec: (editor: any) => {
                if (this.searchPositionStart) {
                    return;
                }

                const cursorPosition = this.getCursorPosition();
                const lineContent = editor.session.getLine(cursorPosition.row).substring(0, cursorPosition.column);

                editor.insert(this.getAssociatedInputCharacter());
                if (cursorPosition.column === 0 || lineContent.slice(-1).match(/\s/)) {
                    this.searchPositionStart = cursorPosition;
                    this.selectWithSearchComponent?.open();
                    this.aceEditor.focus();
                }
            },
        } as any);
    }

    setSelectWithSearchComponent(component: SelectWithSearchComponent) {
        this.selectWithSearchComponent = component;
    }

    insertSelection(selected: SelectableItem | undefined) {
        if (selected !== undefined) {
            const cursorPosition = this.aceEditor.getCursorPosition() as { row: number; column: number };

            this.aceEditor.session
                .getDocument()
                .removeInLine(cursorPosition.row, this.searchPositionStart?.row === cursorPosition.row ? this.searchPositionStart.column : 0, cursorPosition.column);

            this.searchPositionStart = undefined;

            this.insertText(this.selectionToText(selected));
        }

        this.searchPositionStart = undefined;
        this.aceEditor.focus();
    }

    abstract performSearch(searchTerm: string): Observable<HttpResponse<SelectableItem[]>>;

    protected abstract selectionToText(selected: any): string;

    protected abstract getAssociatedInputCharacter(): string;

    getCursorScreenPosition(): any {
        const cursorPosition = super.getCursorPosition();
        return this.aceEditor.renderer.textToScreenCoordinates(cursorPosition.row, cursorPosition.column);
    }

    updateSearchTerm() {
        if (!this.searchPositionStart) {
            return;
        }

        const cursorPosition = this.aceEditor.getCursorPosition();
        const lineContent = this.aceEditor.session.getLine(cursorPosition.row);

        const lastAtIndex = lineContent
            .substring(cursorPosition.row === this.searchPositionStart.row ? this.searchPositionStart.column : 0, cursorPosition.column + 1)
            .lastIndexOf(this.getAssociatedInputCharacter());

        if (lastAtIndex >= 0) {
            const searchTerm = lineContent
                .substring(0, cursorPosition.column + 1)
                .split(this.getAssociatedInputCharacter())
                .pop();

            this.selectWithSearchComponent.updateSearchTerm(searchTerm);
        } else {
            this.selectWithSearchComponent.close();
        }
    }
}
