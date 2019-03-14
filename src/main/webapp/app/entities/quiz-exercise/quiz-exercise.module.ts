import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

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
import { SortByModule } from 'app/components/pipes';
import { ArTEMiSQuizEditModule } from 'app/quiz/edit';
import { ArTEMiSQuizReEvaluateModule } from 'app/quiz/re-evaluate';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArTEMiSCategorySelectorModule } from 'app/components/category-selector/category-selector.module';
import { ArTEMiSDifficultyPickerModule } from 'app/components/exercise/difficulty-picker/difficulty-picker.module';

const ENTITY_STATES = [...quizExerciseRoute, ...quizExercisePopupRoute];

@NgModule({
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), SortByModule, ArTEMiSQuizEditModule, ArTEMiSQuizReEvaluateModule, FormDateTimePickerModule, ArTEMiSCategorySelectorModule, ArTEMiSDifficultyPickerModule],
    exports: [QuizExerciseComponent],
    declarations: [QuizExerciseComponent, QuizExerciseDeleteDialogComponent, QuizExerciseDeletePopupComponent, QuizExerciseResetDialogComponent, QuizExerciseResetPopupComponent, QuizExerciseDetailComponent],
    entryComponents: [
        QuizExerciseComponent,
        QuizExerciseDeleteDialogComponent,
        QuizExerciseDeletePopupComponent,
        QuizExerciseResetDialogComponent,
        QuizExerciseResetPopupComponent,
        QuizExerciseDetailComponent
    ],
    providers: [
        QuizExerciseService,
        QuizExercisePopupService,
        PendingChangesGuard,
        { provide: JhiLanguageService, useClass: JhiLanguageService }],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSQuizExerciseModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
