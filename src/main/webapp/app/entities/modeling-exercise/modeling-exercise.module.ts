import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArtemisSharedModule } from 'app/shared';
import {
    ModelingExerciseComponent,
    ModelingExerciseDetailComponent,
    ModelingExerciseDialogComponent,
    ModelingExercisePopupComponent,
    modelingExercisePopupRoute,
    ModelingExercisePopupService,
    modelingExerciseRoute,
    ModelingExerciseService,
} from './';
import { SortByModule } from 'app/components/pipes';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';
import { ArtemisCategorySelectorModule } from 'app/components/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/components/exercise/difficulty-picker/difficulty-picker.module';
import { ArtemisDeleteDialogModule } from 'app/delete-dialog/delete-dialog.module';
import { DeleteDialogComponent } from 'app/delete-dialog/delete-dialog.component';

const ENTITY_STATES = [...modelingExerciseRoute, ...modelingExercisePopupRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        FormDateTimePickerModule,
        ArtemisMarkdownEditorModule,
        ArtemisCategorySelectorModule,
        ArtemisDifficultyPickerModule,
        ArtemisDeleteDialogModule,
    ],
    declarations: [ModelingExerciseComponent, ModelingExerciseDetailComponent, ModelingExerciseDialogComponent, ModelingExercisePopupComponent],
    entryComponents: [ModelingExerciseComponent, ModelingExerciseDialogComponent, ModelingExercisePopupComponent, DeleteDialogComponent],
    providers: [ModelingExerciseService, ModelingExercisePopupService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
    exports: [ModelingExerciseComponent],
})
export class ArtemisModelingExerciseModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
