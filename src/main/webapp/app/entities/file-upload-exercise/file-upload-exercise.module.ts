import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared';
import { FileUploadExerciseComponent, FileUploadExerciseDetailComponent, fileUploadExerciseRoute, FileUploadExerciseService, FileUploadExerciseUpdateComponent } from './';
import { SortByModule } from 'app/components/pipes';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/components/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/components/exercise/difficulty-picker/difficulty-picker.module';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';
import { ArtemisPresentationScoreModule } from 'app/components/exercise/presentation-score/presentation-score.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

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
        ArtemisPresentationScoreModule,
        ArtemisSharedComponentModule,
    ],
    declarations: [FileUploadExerciseComponent, FileUploadExerciseDetailComponent, FileUploadExerciseUpdateComponent],
    exports: [FileUploadExerciseComponent],
    providers: [FileUploadExerciseService],
})
export class ArtemisFileUploadExerciseModule {}
