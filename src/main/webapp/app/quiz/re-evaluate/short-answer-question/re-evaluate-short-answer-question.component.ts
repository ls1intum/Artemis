import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ShortAnswerQuestion } from '../../../entities/short-answer-question';

@Component({
    selector: 'jhi-re-evaluate-short-answer-question',
    template: `<jhi-edit-short-answer-question [question]="question"
                                                [questionIndex]="questionIndex"
                                                [reEvaluationInProgress]="true"
                                                (questionUpdated)="questionUpdated.emit()"
                                                (questionDeleted)="questionDeleted.emit()"
                                                (questionMoveUp)="questionMoveUp.emit()"
                                                (questionMoveDown)="questionMoveDown.emit()">
               </jhi-edit-short-answer-question>`,
    providers: []
})
export class ReEvaluateShortAnswerQuestionComponent {
    @Input()
    question: ShortAnswerQuestion;
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
