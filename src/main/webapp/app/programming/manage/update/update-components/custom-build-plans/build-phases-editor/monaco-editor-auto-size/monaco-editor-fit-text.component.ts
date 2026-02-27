import { Component, effect, model, signal, viewChild } from '@angular/core';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';

@Component({
    selector: 'jhi-monaco-editor-fit-text',
    templateUrl: './monaco-editor-fit-text.html',
    imports: [MonacoEditorComponent],
})
export class MonacoEditorFitTextComponent {
    readonly text = model<string>('');
    protected readonly editorHeight = signal(0);

    private readonly editor = viewChild.required(MonacoEditorComponent);

    // TODO MHT make ctrl/cmd + z work in phase script editor

    constructor() {
        effect(() => {
            this.editor().changeModel('', this.text(), 'shell');
        });
    }
}
