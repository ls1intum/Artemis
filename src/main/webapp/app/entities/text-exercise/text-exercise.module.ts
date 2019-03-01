import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from 'app/shared';
import {
    TextExerciseComponent,
    TextExerciseDeleteDialogComponent,
    TextExerciseDeletePopupComponent,
    TextExerciseDetailComponent,
    TextExerciseDialogComponent,
    TextExercisePopupComponent,
    textExercisePopupRoute,
    TextExercisePopupService,
    textExerciseRoute,
    TextExerciseService,
    TextExerciseUpdateComponent
} from './';
import { SortByModule } from 'app/components/pipes';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';

const ENTITY_STATES = [...textExerciseRoute, ...textExercisePopupRoute];

@NgModule({
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), SortByModule, FormDateTimePickerModule],
    declarations: [
        TextExerciseComponent,
        TextExerciseDetailComponent,
        TextExerciseUpdateComponent,
        TextExerciseDialogComponent,
        TextExerciseDeleteDialogComponent,
        TextExercisePopupComponent,
        TextExerciseDeletePopupComponent
    ],
    entryComponents: [
        TextExerciseComponent,
        TextExerciseDialogComponent,
        TextExerciseUpdateComponent,
        TextExercisePopupComponent,
        TextExerciseDeleteDialogComponent,
        TextExerciseDeletePopupComponent
    ],
  providers: [TextExerciseService, TextExercisePopupService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
    exports: [TextExerciseComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSTextExerciseModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
