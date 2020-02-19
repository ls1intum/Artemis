import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/shared/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { ArtemisQuizEditModule } from 'app/quiz/edit/quiz-edit.module';
import { QuizExerciseComponent } from 'app/exercises/quiz/manage/quiz-exercise.component';
import { QuizExercisePopupService } from 'app/exercises/quiz/manage/quiz-exercise-popup.service';
import { ArtemisQuizReEvaluateModule } from 'app/quiz/re-evaluate/quiz-re-evaluate.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { quizExerciseRoute } from 'app/exercises/quiz/manage/quiz-exercise.route';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { QuizExerciseDetailComponent } from 'app/exercises/quiz/manage/quiz-exercise-detail.component';

const ENTITY_STATES = [...quizExerciseRoute];

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
    declarations: [QuizExerciseComponent, QuizExerciseDetailComponent],
    entryComponents: [QuizExerciseComponent, QuizExerciseDetailComponent],
    providers: [QuizExerciseService, QuizExercisePopupService, PendingChangesGuard],
})
export class ArtemisQuizExerciseModule {}
