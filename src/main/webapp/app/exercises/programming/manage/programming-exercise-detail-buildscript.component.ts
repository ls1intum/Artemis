import { Component, Input, ViewChild } from '@angular/core';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';

@Component({
    selector: 'jhi-programming-exercise-detail-buildscript',
    templateUrl: './programming-exercise-detail-buildscript.component.html',
})
export class ProgrammingExerciseDetailBuildscriptComponent {
    @Input() private script: string;
    private _editor?: AceEditorComponent;

    @ViewChild('editor', { static: false }) set editor(value: AceEditorComponent) {
        this._editor = value;
        if (this._editor) {
            this.setupEditor();
            this._editor.setText(this.script);
        }
    }

    get editor(): AceEditorComponent | undefined {
        return this._editor;
    }

    setupEditor(): void {
        if (!this._editor) {
            return;
        }
        const lines = this.script.split('\n').length;
        this._editor.getEditor().setOptions({
            animatedScroll: true,
            maxLines: Math.max(30, lines),
            showPrintMargin: false,
            readOnly: true,
            highlightActiveLine: false,
            highlightGutterLine: false,
            minLines: Math.min(30, lines),
            mode: 'ace/mode/sh',
        });
        this._editor.getEditor().renderer.setOptions({
            showFoldWidgets: false,
        });
    }
}
