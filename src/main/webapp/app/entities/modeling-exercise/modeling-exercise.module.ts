import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from 'app/shared';
import {
    ModelingExerciseComponent,
    ModelingExerciseDeleteDialogComponent,
    ModelingExerciseDeletePopupComponent,
    ModelingExerciseDetailComponent,
    ModelingExerciseDialogComponent,
    ModelingExercisePopupComponent,
    modelingExercisePopupRoute,
    ModelingExercisePopupService,
    modelingExerciseRoute,
    ModelingExerciseService
} from './';
import { SortByModule } from 'app/components/pipes';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import {ArTEMiSMarkdownEditorModule} from 'app/markdown-editor';

const ENTITY_STATES = [...modelingExerciseRoute, ...modelingExercisePopupRoute];

@NgModule({
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), SortByModule, FormDateTimePickerModule, ArTEMiSMarkdownEditorModule],
    declarations: [
        ModelingExerciseComponent,
        ModelingExerciseDetailComponent,
        ModelingExerciseDialogComponent,
        ModelingExerciseDeleteDialogComponent,
        ModelingExercisePopupComponent,
        ModelingExerciseDeletePopupComponent
    ],
    entryComponents: [
        ModelingExerciseComponent,
        ModelingExerciseDialogComponent,
        ModelingExercisePopupComponent,
        ModelingExerciseDeleteDialogComponent,
        ModelingExerciseDeletePopupComponent
    ],
  providers: [
        ModelingExerciseService,
        ModelingExercisePopupService,
        { provide: JhiLanguageService, useClass: JhiLanguageService }
  ],
    exports: [ModelingExerciseComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSModelingExerciseModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
