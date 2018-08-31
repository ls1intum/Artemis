import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../../shared';
import { QuizExerciseComponent, QuizExerciseDeleteDialogComponent, QuizExerciseDeletePopupComponent, quizExercisePopupRoute, QuizExercisePopupService, quizExerciseRoute, QuizExerciseService, QuizReEvaluateService } from './';
import { QuizReEvaluateWarningComponent } from '../../quiz/re-evaluate/quiz-re-evaluate-warning.component';
import { SortByModule } from '../../components/pipes';

const ENTITY_STATES = [
    ...quizExerciseRoute,
    ...quizExercisePopupRoute,
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule
    ],
    declarations: [
        QuizExerciseComponent,
        QuizExerciseDeleteDialogComponent,
        QuizExerciseDeletePopupComponent,
        QuizReEvaluateWarningComponent
    ],
    entryComponents: [
        QuizExerciseComponent,
        QuizExerciseDeleteDialogComponent,
        QuizExerciseDeletePopupComponent,
        QuizReEvaluateWarningComponent
    ],
    providers: [
        QuizExerciseService,
        QuizExercisePopupService,
        QuizReEvaluateService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSQuizExerciseModule {}
