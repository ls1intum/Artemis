import { AfterViewInit, Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { Observable, of } from 'rxjs';

import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';

import { ExpressionChildInput, ExpressionResult } from './input';

@Component({
    selector: 'jhi-math-task-expression-editor-text-input',
    templateUrl: './text-input.component.html',
    styleUrl: './text-input.component.scss',
})
export class TextInputComponent implements ExpressionChildInput<string>, AfterViewInit {
    @ViewChild('editor', { static: false })
    private editorRef: AceEditorComponent;

    protected editorHeight: string = 'auto';
    protected commands = [new KatexCommand()];

    protected get expression(): string | null {
        return this.value;
    }

    protected set expression(value: string | null) {
        this.value = value;
        this.valueChange.emit(value);
    }

    @Input()
    value: string | null;

    @Output()
    valueChange = new EventEmitter<string | null>();

    @Input()
    disabled: boolean;

    @Input()
    exerciseId: number;

    getExpression(): Observable<ExpressionResult> {
        return of({ expression: this.expression });
    }

    ngAfterViewInit(): void {
        const editor = this.editorRef.getEditor();

        this.commands.forEach((command) => command.setEditor(editor));

        editor.renderer.setShowGutter(false);
        editor.renderer.setPadding(8);
        editor.renderer.setScrollMargin(8, 8, 0, 0);
        editor.setHighlightActiveLine(false);
        editor.setShowPrintMargin(false);
        editor.clearSelection();
        // editor.setAutoScrollEditorIntoView(true);
        editor.setOptions({
            wrap: true,
            maxLines: 1000,
            placeholder: 'Enter a LaTeX expression',
        });
    }
}
