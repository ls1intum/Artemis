import { ChangeDetectionStrategy, Component, effect, model, signal, viewChild } from '@angular/core';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';

@Component({
    selector: 'jhi-monaco-editor-fit-text',
    templateUrl: './monaco-editor-fit-text.component.html',
    imports: [MonacoEditorComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
/**
 * Wraps the Monaco editor and keeps its container height synchronized with its content.
 */
export class MonacoEditorFitTextComponent {
    readonly text = model<string>('');
    protected readonly editorHeight = signal(0);

    private readonly editor = viewChild.required(MonacoEditorComponent);
    private initialized = false;

    /**
     * Synchronizes the external text model with the Monaco editor instance.
     * Sets the languageId to shell for right syntax highlighting.
     */
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
