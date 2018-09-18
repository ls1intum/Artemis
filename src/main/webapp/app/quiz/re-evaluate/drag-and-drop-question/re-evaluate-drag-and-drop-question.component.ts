import { Component, Input, Output, EventEmitter } from '@angular/core';
import { DragAndDropQuestion } from '../../../entities/drag-and-drop-question';

@Component({
    selector: 'jhi-re-evaluate-drag-and-drop-question',
    template: `<jhi-edit-drag-and-drop-question [question]="question"
                                                [questionIndex]="questionIndex"
                                                (questionUpdated)="questionUpdated.emit()"
                                                (questionDeleted)="questionDeleted.emit()"
                                                (questionMoveUp)="questionMoveUp.emit()"
                                                (questionMoveDown)="questionMoveDown.emit()">
               </jhi-edit-drag-and-drop-question>`,
    providers: []
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
    questionUpdated = new EventEmitter<object>();
    @Output()
    questionDeleted = new EventEmitter<object>();
    @Output()
    questionMoveUp = new EventEmitter<object>();
    @Output()
    questionMoveDown = new EventEmitter<object>();

    constructor() {}
}
