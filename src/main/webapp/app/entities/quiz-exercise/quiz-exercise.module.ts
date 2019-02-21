import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule, PendingChangesGuard } from '../../shared';
import {
    QuizExerciseComponent,
    QuizExerciseDeleteDialogComponent,
    QuizExerciseDeletePopupComponent,
    QuizExerciseDetailComponent,
    quizExercisePopupRoute,
    QuizExercisePopupService,
    QuizExerciseResetDialogComponent,
    QuizExerciseResetPopupComponent,
    quizExerciseRoute,
    QuizExerciseService
} from './';
import { SortByModule } from '../../components/pipes';
import { ArTEMiSQuizEditModule } from '../../quiz/edit';
import { ArTEMiSQuizReEvaluateModule } from '../../quiz/re-evaluate';

const ENTITY_STATES = [...quizExerciseRoute, ...quizExercisePopupRoute];

@NgModule({
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), SortByModule, ArTEMiSQuizEditModule, ArTEMiSQuizReEvaluateModule],
    declarations: [QuizExerciseComponent, QuizExerciseDeleteDialogComponent, QuizExerciseDeletePopupComponent, QuizExerciseResetDialogComponent, QuizExerciseResetPopupComponent, QuizExerciseDetailComponent],
    entryComponents: [
        QuizExerciseComponent,
        QuizExerciseDeleteDialogComponent,
        QuizExerciseDeletePopupComponent,
        QuizExerciseResetDialogComponent,
        QuizExerciseResetPopupComponent,
        QuizExerciseDetailComponent
    ],
    providers: [QuizExerciseService, QuizExercisePopupService, PendingChangesGuard],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSQuizExerciseModule {}
