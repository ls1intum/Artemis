import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArtemisSharedModule, PendingChangesGuard } from '../../shared';
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
    QuizExerciseService,
} from './';
import { SortByModule } from 'app/components/pipes';
import { ArtemisQuizEditModule } from 'app/quiz/edit';
import { ArtemisQuizReEvaluateModule } from 'app/quiz/re-evaluate';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/components/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/components/exercise/difficulty-picker/difficulty-picker.module';

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
    declarations: [
        QuizExerciseComponent,
        QuizExerciseDeleteDialogComponent,
        QuizExerciseDeletePopupComponent,
        QuizExerciseResetDialogComponent,
        QuizExerciseResetPopupComponent,
        QuizExerciseDetailComponent,
    ],
    entryComponents: [
        QuizExerciseComponent,
        QuizExerciseDeleteDialogComponent,
        QuizExerciseDeletePopupComponent,
        QuizExerciseResetDialogComponent,
        QuizExerciseResetPopupComponent,
        QuizExerciseDetailComponent,
    ],
    providers: [QuizExerciseService, QuizExercisePopupService, PendingChangesGuard, { provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArtemisQuizExerciseModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
