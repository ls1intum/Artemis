import { Component, EventEmitter, Input, Output, input } from '@angular/core';
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

    // TODO: Skipped for migration because:
    //  Accessor inputs cannot be migrated as they are too complex.
    @Input() set question(question: QuizQuestion) {
        this.shortAnswerQuestion = question as ShortAnswerQuestion;
    }
    readonly questionIndex = input<number>(undefined!);

    @Output()
    questionUpdated = new EventEmitter();
    @Output()
    questionDeleted = new EventEmitter();
    @Output()
    questionMoveUp = new EventEmitter();
    @Output()
    questionMoveDown = new EventEmitter();
}
