import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from '../../shared';
import {
    ExerciseLtiConfigurationDialogComponent,
    ExerciseLtiConfigurationPopupComponent,
    ExerciseLtiConfigurationService,
    exercisePopupRoute,
    ExercisePopupService,
    ExerciseResetDialogComponent,
    ExerciseResetPopupComponent,
    ExerciseService
} from './';

const ENTITY_STATES = [...exercisePopupRoute];

@NgModule({
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        ExerciseLtiConfigurationDialogComponent,
        ExerciseLtiConfigurationPopupComponent,
        ExerciseResetDialogComponent,
        ExerciseResetPopupComponent
    ],
    entryComponents: [
        ExerciseLtiConfigurationDialogComponent,
        ExerciseLtiConfigurationPopupComponent,
        ExerciseResetDialogComponent,
        ExerciseResetPopupComponent
    ],
    providers: [
        ExercisePopupService,
        ExerciseService,
        ExerciseLtiConfigurationService,
        { provide: JhiLanguageService, useClass: JhiLanguageService }],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSExerciseModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
