import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';

@Component({
    selector: 'jhi-solution-entry',
    templateUrl: './solution-entry.component.html',
    styleUrls: ['./solution-entry.component.scss'],
})
export class SolutionEntryComponent implements OnInit {
    @ViewChild('editor', { static: true })
    editor: MonacoEditorComponent;

    @Input()
    solutionEntry: ProgrammingExerciseSolutionEntry;
    @Input()
    enableEditing: boolean;

    @Output()
    onRemoveEntry: EventEmitter<void> = new EventEmitter();

    editorHeight = 20;

    protected readonly faTimes = faTimes;

    ngOnInit(): void {
        this.setupEditor();
    }

    onEditorContentChange(value: string): void {
        this.solutionEntry.code = value;
    }

    onContentSizeChange(contentHeight: number): void {
        this.editorHeight = contentHeight;
    }

    setupEditor(): void {
        const startLine = this.solutionEntry.line ?? 1;
        this.editor.setStartLineNumber(startLine);
        this.editor.changeModel(this.solutionEntry.filePath ?? 'file', this.solutionEntry.code ?? '');
        this.editor.layout();
        // We manually fetch the initial content height, as the editor does not provide it immediately
        this.editorHeight = this.editor.getContentHeight();
    }
}
