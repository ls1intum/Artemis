import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArtemisSharedModule } from 'app/shared';
import {
    TextExerciseComponent,
    TextExerciseDetailComponent,
    TextExerciseDialogComponent,
    TextExercisePopupComponent,
    textExercisePopupRoute,
    TextExercisePopupService,
    textExerciseRoute,
    TextExerciseService,
    TextExerciseUpdateComponent,
} from './';
import { SortByModule } from 'app/components/pipes';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/components/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/components/exercise/difficulty-picker/difficulty-picker.module';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';
import { ArtemisDeleteDialogModule } from 'app/delete-dialog/delete-dialog.module';
import { DeleteDialogComponent } from 'app/delete-dialog/delete-dialog.component';

const ENTITY_STATES = [...textExerciseRoute, ...textExercisePopupRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        FormDateTimePickerModule,
        ArtemisCategorySelectorModule,
        ArtemisDifficultyPickerModule,
        ArtemisMarkdownEditorModule,
        ArtemisDeleteDialogModule,
    ],
    declarations: [TextExerciseComponent, TextExerciseDetailComponent, TextExerciseUpdateComponent, TextExerciseDialogComponent, TextExercisePopupComponent],
    entryComponents: [TextExerciseComponent, TextExerciseDialogComponent, TextExerciseUpdateComponent, TextExercisePopupComponent, DeleteDialogComponent],
    providers: [TextExerciseService, TextExercisePopupService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
    exports: [TextExerciseComponent],
})
export class ArtemisTextExerciseModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
