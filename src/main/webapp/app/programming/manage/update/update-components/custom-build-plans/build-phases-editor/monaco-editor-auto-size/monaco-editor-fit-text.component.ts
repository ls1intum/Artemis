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
    private initialized = false;

    constructor() {
        effect(() => {
            const newText = this.text();
            const editor = this.editor();
            if (!this.initialized) {
                editor.changeModel('', newText, 'shell');
                this.initialized = true;
            } else if (editor.getText() !== newText) {
                editor.setText(newText);
            }
        });
    }
}
