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
    FileUploadExerciseUpdateComponent,
} from './';
import { SortByModule } from 'app/components/pipes';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArTEMiSCategorySelectorModule } from 'app/components/category-selector/category-selector.module';
import { ArTEMiSDifficultyPickerModule } from 'app/components/exercise/difficulty-picker/difficulty-picker.module';
import { ArTEMiSMarkdownEditorModule } from 'app/markdown-editor';

const ENTITY_STATES = [...fileUploadExerciseRoute, ...fileUploadExercisePopupRoute];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        FormDateTimePickerModule,
        ArTEMiSCategorySelectorModule,
        ArTEMiSDifficultyPickerModule,
        ArTEMiSMarkdownEditorModule,
    ],
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
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
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
