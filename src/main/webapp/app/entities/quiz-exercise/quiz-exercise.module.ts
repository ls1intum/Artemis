import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    QuizExerciseComponent,
    QuizExerciseDetailComponent,
    QuizExerciseUpdateComponent,
    QuizExerciseDeletePopupComponent,
    QuizExerciseDeleteDialogComponent,
    quizExerciseRoute,
    quizExercisePopupRoute
} from './';

const ENTITY_STATES = [...quizExerciseRoute, ...quizExercisePopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        QuizExerciseComponent,
        QuizExerciseDetailComponent,
        QuizExerciseUpdateComponent,
        QuizExerciseDeleteDialogComponent,
        QuizExerciseDeletePopupComponent
    ],
    entryComponents: [
        QuizExerciseComponent,
        QuizExerciseUpdateComponent,
        QuizExerciseDeleteDialogComponent,
        QuizExerciseDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSQuizExerciseModule {}
