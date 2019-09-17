import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared';
import {
    TextExerciseComponent,
    TextExerciseDeleteDialogComponent,
    TextExerciseDeletePopupComponent,
    TextExerciseDetailComponent,
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
    ],
    declarations: [TextExerciseComponent, TextExerciseDetailComponent, TextExerciseUpdateComponent, TextExerciseDeleteDialogComponent, TextExerciseDeletePopupComponent],
    entryComponents: [TextExerciseDeleteDialogComponent, TextExerciseDeletePopupComponent],
    providers: [TextExerciseService, TextExercisePopupService],
    exports: [TextExerciseComponent],
})
export class ArtemisTextExerciseModule {}
