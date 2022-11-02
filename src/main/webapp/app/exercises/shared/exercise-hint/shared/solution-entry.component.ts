import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';

@Component({
    selector: 'jhi-solution-entry',
    templateUrl: './solution-entry.component.html',
})
export class SolutionEntryComponent implements OnInit {
    @ViewChild('editor', { static: true })
    editor: AceEditorComponent;

    @Input()
    solutionEntry: ProgrammingExerciseSolutionEntry;
    @Input()
    enableEditing: boolean;

    @Output()
    onRemoveEntry: EventEmitter<void> = new EventEmitter();

    faTimes = faTimes;

    constructor(protected route: ActivatedRoute) {}

    ngOnInit() {
        this.setupEditor();
    }

    emitRemovalEvent() {
        this.onRemoveEntry.emit();
    }

    onEditorContentChange(value: any) {
        this.solutionEntry.code = value;
    }

    private setupEditor() {
        const line = this.solutionEntry.line;

        this.editor.getEditor().setOptions({
            animatedScroll: true,
            maxLines: Infinity,
            showPrintMargin: false,
        });
        // Ensure that the line counter is according to the solution entry
        this.editor.getEditor().session.gutterRenderer = {
            getWidth(session: any, lastLineNumber: number, config: any) {
                return this.getText(session, lastLineNumber).toString().length * config.characterWidth;
            },
            getText(session: any, row: number): string | number {
                return !line ? '' : row + line;
            },
        };
        this.editor.getEditor().getSession().setValue(this.solutionEntry.code);
        this.editor.getEditor().resize();
    }
}
