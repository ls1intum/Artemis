import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { DragAndDropQuestionEditComponent } from 'app/exercises/quiz/manage/drag-and-drop-question/drag-and-drop-question-edit.component';

@Component({
    selector: 'jhi-re-evaluate-drag-and-drop-question',
    template: `
        <jhi-drag-and-drop-question-edit
            [question]="question"
            [questionIndex]="questionIndex"
            [reEvaluationInProgress]="true"
            [filePool]="fileMap"
            (questionUpdated)="questionUpdated.emit()"
            (questionDeleted)="questionDeleted.emit()"
            (questionMoveUp)="questionMoveUp.emit()"
            (questionMoveDown)="questionMoveDown.emit()"
            (addNewFile)="handleAddFile($event)"
            (removeFile)="handleRemoveFile($event)"
        >
        </jhi-drag-and-drop-question-edit>
    `,
    providers: [],
})
export class ReEvaluateDragAndDropQuestionComponent {
    /**
     question: '=',
     onDelete: '&',
     onUpdated: '&',
     questionIndex: '<',
     onMoveUp: '&',
     onMoveDown: '&'
     */

    @ViewChild(DragAndDropQuestionEditComponent)
    dragAndDropQuestionEditComponent: DragAndDropQuestionEditComponent;

    @Input()
    question: DragAndDropQuestion;
    @Input()
    questionIndex: number;

    @Output()
    questionUpdated = new EventEmitter<void>();
    @Output()
    questionDeleted = new EventEmitter<void>();
    @Output()
    questionMoveUp = new EventEmitter<void>();
    @Output()
    questionMoveDown = new EventEmitter<void>();

    fileMap = new Map<string, { path?: string; file: File }>();

    constructor() {}

    /**
     * Add the given file to the fileMap for later upload.
     * @param event the event containing the file and its name. The name provided may be different from the actual file name but has to correspond to the name set in the entity object.
     */
    handleAddFile(event: { fileName: string; path?: string; file: File }): void {
        this.fileMap.set(event.fileName, { path: event.path, file: event.file });
    }

    /**
     * Remove the given file from the fileMap.
     * @param fileName the name of the file to be removed
     */
    handleRemoveFile(fileName: string): void {
        this.fileMap.delete(fileName);
    }
}
