import { Component, input, output } from '@angular/core';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { MultipleChoiceQuestionEditComponent } from 'app/quiz/manage/multiple-choice-question/multiple-choice-question-edit.component';

@Component({
    selector: 'jhi-multiple-choice',
    imports: [MultipleChoiceQuestionEditComponent],
    template: ` <jhi-multiple-choice-question-edit
        [question]="question()"
        [questionIndex]="questionIndex()"
        [reEvaluationInProgress]="true"
        (questionUpdated)="questionUpdated.emit()"
        (questionDeleted)="questionDeleted.emit()"
        (questionMoveUp)="questionMoveUp.emit()"
        (questionMoveDown)="questionMoveDown.emit()"
    />`,
})
export class ReEvaluateMultipleChoiceQuestionComponent {
    question = input.required<MultipleChoiceQuestion>();
    questionIndex = input.required<number>();

    questionDeleted = output<void>();
    questionUpdated = output<void>();
    questionMoveUp = output<void>();
    questionMoveDown = output<void>();
}
