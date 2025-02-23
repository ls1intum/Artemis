import { Component, EventEmitter, Input, Output } from '@angular/core';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerQuestionEditComponent } from '../../short-answer-question/short-answer-question-edit.component';

@Component({
    selector: 'jhi-re-evaluate-short-answer-question',
    template: `
        <jhi-short-answer-question-edit
            [question]="shortAnswerQuestion"
            [questionIndex]="questionIndex"
            [reEvaluationInProgress]="true"
            (questionUpdated)="questionUpdated.emit()"
            (questionDeleted)="questionDeleted.emit()"
            (questionMoveUp)="questionMoveUp.emit()"
            (questionMoveDown)="questionMoveDown.emit()"
        />
    `,
    providers: [],
    imports: [ShortAnswerQuestionEditComponent],
})
export class ReEvaluateShortAnswerQuestionComponent {
    shortAnswerQuestion: ShortAnswerQuestion;

    @Input() set question(question: QuizQuestion) {
        this.shortAnswerQuestion = question as ShortAnswerQuestion;
    }
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
}
