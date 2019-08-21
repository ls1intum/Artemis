import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArtemisSharedModule } from 'app/shared';
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
    FileUploadExerciseUpdateComponent,
} from './';
import { SortByModule } from 'app/components/pipes';

const ENTITY_STATES = [...fileUploadExerciseRoute, ...fileUploadExercisePopupRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), SortByModule],
    declarations: [
        FileUploadExerciseComponent,
        FileUploadExerciseDetailComponent,
        FileUploadExerciseUpdateComponent,
        FileUploadExerciseDialogComponent,
        FileUploadExerciseDeleteDialogComponent,
        FileUploadExercisePopupComponent,
        FileUploadExerciseDeletePopupComponent,
    ],
    entryComponents: [
        FileUploadExerciseComponent,
        FileUploadExerciseUpdateComponent,
        FileUploadExerciseDialogComponent,
        FileUploadExercisePopupComponent,
        FileUploadExerciseDeleteDialogComponent,
        FileUploadExerciseDeletePopupComponent,
    ],
    exports: [FileUploadExerciseComponent],
    providers: [FileUploadExerciseService, FileUploadExercisePopupService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArtemisFileUploadExerciseModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
