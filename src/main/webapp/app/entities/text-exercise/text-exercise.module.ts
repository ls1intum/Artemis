import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared';
import { TextExerciseComponent, TextExerciseDetailComponent, textExerciseRoute, TextExerciseService, TextExerciseUpdateComponent } from './';
import { SortByModule } from 'app/components/pipes';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/components/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/components/exercise/difficulty-picker/difficulty-picker.module';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { ArtemisPresentationScoreModule } from 'app/components/exercise/presentation-score/presentation-score.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment-shared/assessment-shared.module';

const ENTITY_STATES = [...textExerciseRoute];

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
        ArtemisAssessmentSharedModule,
    ],
    declarations: [TextExerciseComponent, TextExerciseDetailComponent, TextExerciseUpdateComponent],
    entryComponents: [TextExerciseUpdateComponent, DeleteDialogComponent],
    providers: [TextExerciseService],
    exports: [TextExerciseComponent],
})
export class ArtemisTextExerciseModule {}
