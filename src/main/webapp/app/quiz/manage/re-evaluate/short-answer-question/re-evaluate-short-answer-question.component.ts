import { Component, effect, input, output } from '@angular/core';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { ShortAnswerQuestionEditComponent } from 'app/quiz/manage/short-answer-question/short-answer-question-edit.component';

@Component({
    selector: 'jhi-re-evaluate-short-answer-question',
    template: `
        <jhi-short-answer-question-edit
            [question]="shortAnswerQuestion"
            [questionIndex]="questionIndex()"
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

    question = input.required<QuizQuestion>();
    questionIndex = input.required<number>();

    questionUpdated = output();
    questionDeleted = output();
    questionMoveUp = output();
    questionMoveDown = output();

    constructor() {
        effect(() => {
            this.shortAnswerQuestion = this.question() as ShortAnswerQuestion;
        });
    }
}
