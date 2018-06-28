import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import {
    QuizExerciseService,
    QuizExercisePopupService,
    QuizExerciseComponent,
    QuizExerciseDetailComponent,
    QuizExerciseDialogComponent,
    QuizExercisePopupComponent,
    QuizExerciseDeletePopupComponent,
    QuizExerciseDeleteDialogComponent,
    quizExerciseRoute,
    quizExercisePopupRoute,
} from './';

const ENTITY_STATES = [
    ...quizExerciseRoute,
    ...quizExercisePopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        QuizExerciseComponent,
        QuizExerciseDetailComponent,
        QuizExerciseDialogComponent,
        QuizExerciseDeleteDialogComponent,
        QuizExercisePopupComponent,
        QuizExerciseDeletePopupComponent,
    ],
    entryComponents: [
        QuizExerciseComponent,
        QuizExerciseDialogComponent,
        QuizExercisePopupComponent,
        QuizExerciseDeleteDialogComponent,
        QuizExerciseDeletePopupComponent,
    ],
    providers: [
        QuizExerciseService,
        QuizExercisePopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSQuizExerciseModule {}
