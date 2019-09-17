import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared';
import { ModelingExerciseComponent, ModelingExerciseDetailComponent, ModelingExerciseUpdateComponent, modelingExerciseRoute, ModelingExerciseService } from './';
import { SortByModule } from 'app/components/pipes';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';
import { ArtemisCategorySelectorModule } from 'app/components/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/components/exercise/difficulty-picker/difficulty-picker.module';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';

const ENTITY_STATES = [...modelingExerciseRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        FormDateTimePickerModule,
        ArtemisMarkdownEditorModule,
        ArtemisCategorySelectorModule,
        ArtemisDifficultyPickerModule,
    ],
    declarations: [ModelingExerciseComponent, ModelingExerciseDetailComponent, ModelingExerciseUpdateComponent],
    entryComponents: [ModelingExerciseComponent, DeleteDialogComponent],
    providers: [ModelingExerciseService],
    exports: [ModelingExerciseComponent],
})
export class ArtemisModelingExerciseModule {}
