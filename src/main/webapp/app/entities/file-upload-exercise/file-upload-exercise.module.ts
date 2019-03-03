import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from 'app/shared';
import {
    FileUploadExerciseComponent,
    FileUploadExerciseDeleteDialogComponent,
    FileUploadExerciseDeletePopupComponent,
    FileUploadExerciseDetailComponent,
    FileUploadExerciseDialogComponent,
    FileUploadExercisePopupComponent,
    fileUploadExercisePopupRoute,
    FileUploadExercisePopupService,
    fileUploadExerciseRoute,
    FileUploadExerciseService,
    FileUploadExerciseUpdateComponent
} from './';
import { SortByModule } from 'app/components/pipes';

const ENTITY_STATES = [...fileUploadExerciseRoute, ...fileUploadExercisePopupRoute];

@NgModule({
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), SortByModule],
    declarations: [
        FileUploadExerciseComponent,
        FileUploadExerciseDetailComponent,
        FileUploadExerciseUpdateComponent,
        FileUploadExerciseDialogComponent,
        FileUploadExerciseDeleteDialogComponent,
        FileUploadExercisePopupComponent,
        FileUploadExerciseDeletePopupComponent
    ],
    entryComponents: [
        FileUploadExerciseComponent,
        FileUploadExerciseUpdateComponent,
        FileUploadExerciseDialogComponent,
        FileUploadExercisePopupComponent,
        FileUploadExerciseDeleteDialogComponent,
        FileUploadExerciseDeletePopupComponent
    ],
    providers: [
        FileUploadExerciseService,
        FileUploadExercisePopupService,
        { provide: JhiLanguageService, useClass: JhiLanguageService }
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSFileUploadExerciseModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
