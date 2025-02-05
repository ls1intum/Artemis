import { NgModule } from '@angular/core';

import { DragAndDropQuestionComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { MultipleChoiceQuestionComponent } from 'app/exercises/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { ShortAnswerQuestionComponent } from 'app/exercises/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { DragItemComponent } from './drag-and-drop-question/drag-item.component';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { QuizScoringInfoStudentModalComponent } from 'app/exercises/quiz/shared/questions/quiz-scoring-infostudent-modal/quiz-scoring-info-student-modal.component';

import { MultipleChoiceVisualQuestionComponent } from 'app/exercises/quiz/shared/questions/multiple-choice-question/multiple-choice-visual-question.component';

@NgModule({
    imports: [
        DragDropModule,
        DragItemComponent,
        DragAndDropQuestionComponent,
        MultipleChoiceQuestionComponent,
        MultipleChoiceVisualQuestionComponent,
        ShortAnswerQuestionComponent,
        QuizScoringInfoStudentModalComponent,
    ],
    exports: [DragItemComponent, DragAndDropQuestionComponent, MultipleChoiceQuestionComponent, ShortAnswerQuestionComponent, MultipleChoiceVisualQuestionComponent],
})
export class ArtemisQuizQuestionTypesModule {}
