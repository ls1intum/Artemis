import { AfterViewInit, Component, effect, model, signal, viewChild } from '@angular/core';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';

@Component({
    selector: 'jhi-monaco-editor-fit-text',
    templateUrl: './monaco-editor-fit-text.html',
    imports: [MonacoEditorComponent],
})
export class MonacoEditorFitTextComponent implements AfterViewInit {
    readonly text = model<string>('');
    protected readonly editorHeight = signal(0);

    private readonly editor = viewChild.required(MonacoEditorComponent);

    constructor() {
        effect(() => {
            const newText = this.text();
            const editor = this.editor();
            if (editor.getText() !== newText) {
                editor.setText(newText);
            }
        });
    }

    ngAfterViewInit() {
        this.editor().changeModel('', this.text(), 'shell');
    }
}
