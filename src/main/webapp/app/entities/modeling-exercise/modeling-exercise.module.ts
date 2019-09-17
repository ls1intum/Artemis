import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared';
import {
    ModelingExerciseComponent,
    ModelingExerciseDeleteDialogComponent,
    ModelingExerciseDeletePopupComponent,
    ModelingExerciseDetailComponent,
    modelingExercisePopupRoute,
    ModelingExercisePopupService,
    modelingExerciseRoute,
    ModelingExerciseService,
    ModelingExerciseUpdateComponent,
} from './';
import { SortByModule } from 'app/components/pipes';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';
import { ArtemisCategorySelectorModule } from 'app/components/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/components/exercise/difficulty-picker/difficulty-picker.module';

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
    ],
    declarations: [
        ModelingExerciseComponent,
        ModelingExerciseDetailComponent,
        ModelingExerciseUpdateComponent,
        ModelingExerciseDeleteDialogComponent,
        ModelingExerciseDeletePopupComponent,
    ],
    entryComponents: [ModelingExerciseDeleteDialogComponent, ModelingExerciseDeletePopupComponent],
    providers: [ModelingExerciseService, ModelingExercisePopupService],
    exports: [ModelingExerciseComponent],
})
export class ArtemisModelingExerciseModule {}
