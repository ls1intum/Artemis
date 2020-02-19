import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/components/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/components/exercise/difficulty-picker/difficulty-picker.module';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { ArtemisPresentationScoreModule } from 'app/components/exercise/presentation-score/presentation-score.module';
import { AssessmentInstructionsModule } from 'app/assessment-instructions/assessment-instructions.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment-shared/assessment-shared.module';
import { SortByModule } from 'app/components/pipes/sort-by.module';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor/markdown-editor.module';
import { TextExerciseUpdateComponent } from 'app/entities/text-exercise/text-exercise-update.component';
import { textExerciseRoute } from 'app/entities/text-exercise/text-exercise.route';
import { TextExerciseComponent } from 'app/entities/text-exercise/text-exercise.component';
import { TextExerciseService } from 'app/entities/text-exercise/text-exercise.service';
import { TextExerciseDetailComponent } from 'app/entities/text-exercise/text-exercise-detail.component';
import { StructuredGradingCriterionModule } from 'app/structured-grading-criterion/structured-grading-criterion.module';
import { ArtemisSlideToggleModule } from 'app/components/slide-toggle/slide-toggle.module';

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
        AssessmentInstructionsModule,
        StructuredGradingCriterionModule,
        ArtemisAssessmentSharedModule,
        ArtemisSlideToggleModule,
    ],
    declarations: [TextExerciseComponent, TextExerciseDetailComponent, TextExerciseUpdateComponent],
    entryComponents: [TextExerciseUpdateComponent, DeleteDialogComponent],
    providers: [TextExerciseService],
    exports: [TextExerciseComponent],
})
export class ArtemisTextExerciseModule {}
