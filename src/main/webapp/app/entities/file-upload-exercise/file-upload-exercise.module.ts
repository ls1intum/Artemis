import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared';
import {
    FileUploadExerciseComponent,
    FileUploadExerciseDeleteDialogComponent,
    FileUploadExerciseDetailComponent,
    FileUploadExercisePopupService,
    fileUploadExerciseRoute,
    FileUploadExerciseService,
    FileUploadExerciseUpdateComponent,
} from './';
import { SortByModule } from 'app/components/pipes';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/components/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/components/exercise/difficulty-picker/difficulty-picker.module';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';

const ENTITY_STATES = [...fileUploadExerciseRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        FormDateTimePickerModule,
        ArtemisCategorySelectorModule,
        ArtemisDifficultyPickerModule,
        ArtemisMarkdownEditorModule,
    ],
    declarations: [FileUploadExerciseComponent, FileUploadExerciseDetailComponent, FileUploadExerciseUpdateComponent, FileUploadExerciseDeleteDialogComponent],
    entryComponents: [FileUploadExerciseDeleteDialogComponent],
    exports: [FileUploadExerciseComponent],
    providers: [FileUploadExerciseService, FileUploadExercisePopupService],
})
export class ArtemisFileUploadExerciseModule {}
