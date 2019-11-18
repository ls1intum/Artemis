import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from '../../shared';
import {
    QuizExerciseComponent,
    QuizExerciseDetailComponent,
    quizExercisePopupRoute,
    QuizExercisePopupService,
    QuizExerciseResetDialogComponent,
    QuizExerciseResetPopupComponent,
    quizExerciseRoute,
    QuizExerciseService,
} from './';
import { SortByModule } from 'app/components/pipes';
import { ArtemisQuizEditModule } from 'app/quiz/edit';
import { ArtemisQuizReEvaluateModule } from 'app/quiz/re-evaluate';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/components/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/components/exercise/difficulty-picker/difficulty-picker.module';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';

const ENTITY_STATES = [...quizExerciseRoute, ...quizExercisePopupRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        ArtemisQuizEditModule,
        ArtemisQuizReEvaluateModule,
        FormDateTimePickerModule,
        ArtemisCategorySelectorModule,
        ArtemisDifficultyPickerModule,
    ],
    exports: [QuizExerciseComponent],
    declarations: [QuizExerciseComponent, QuizExerciseResetDialogComponent, QuizExerciseResetPopupComponent, QuizExerciseDetailComponent],
    entryComponents: [QuizExerciseComponent, QuizExerciseResetDialogComponent, QuizExerciseResetPopupComponent, QuizExerciseDetailComponent],
    providers: [QuizExerciseService, QuizExercisePopupService, PendingChangesGuard],
})
export class ArtemisQuizExerciseModule {}
