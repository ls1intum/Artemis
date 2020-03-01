import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';

@Component({
    selector: 'jhi-re-evaluate-short-answer-question',
    template: `
        <jhi-short-answer-question-edit
            [question]="question"
            [questionIndex]="questionIndex"
            [reEvaluationInProgress]="true"
            (questionUpdated)="questionUpdated.emit()"
            (questionDeleted)="questionDeleted.emit()"
            (questionMoveUp)="questionMoveUp.emit()"
            (questionMoveDown)="questionMoveDown.emit()"
        >
        </jhi-short-answer-question-edit>
    `,
    providers: [],
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
