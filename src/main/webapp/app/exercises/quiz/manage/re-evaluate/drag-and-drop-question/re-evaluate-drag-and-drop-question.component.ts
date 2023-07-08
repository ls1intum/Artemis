import { Component, EventEmitter, Input, Output } from '@angular/core';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';

@Component({
    selector: 'jhi-re-evaluate-drag-and-drop-question',
    template: `
        <jhi-drag-and-drop-question-edit
            [question]="question"
            [questionIndex]="questionIndex"
            [reEvaluationInProgress]="true"
            (questionUpdated)="questionUpdated.emit()"
            (questionDeleted)="questionDeleted.emit()"
            (questionMoveUp)="questionMoveUp.emit()"
            (questionMoveDown)="questionMoveDown.emit()"
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

    @Input()
    question: DragAndDropQuestion;
    @Input()
    questionIndex: number;

    @Output()
    questionUpdated = new EventEmitter();
    @Output()
    questionDeleted = new EventEmitter();
    @Output()
    questionMoveUp = new EventEmitter();
    @Output()
    questionMoveDown = new EventEmitter();

    constructor() {}
}
