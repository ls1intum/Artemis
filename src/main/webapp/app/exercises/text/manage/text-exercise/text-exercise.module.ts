import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/shared/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { ArtemisPresentationScoreModule } from 'app/exercises/shared/presentation-score/presentation-score.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { TextExerciseUpdateComponent } from 'app/exercises/text/manage/text-exercise/text-exercise-update.component';
import { textExerciseRoute } from 'app/exercises/text/manage/text-exercise/text-exercise.route';
import { TextExerciseComponent } from 'app/exercises/text/manage/text-exercise/text-exercise.component';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { TextExerciseDetailComponent } from 'app/exercises/text/manage/text-exercise/text-exercise-detail.component';
import { ArtemisTeamConfigFormGroupModule } from 'app/exercises/shared/team-config-form-group/team-config-form-group.module';
import { ArtemisSlideToggleModule } from 'app/exercises/shared/slide-toggle/slide-toggle.module';
import { StructuredGradingCriterionModule } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.module';

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
        ArtemisTeamConfigFormGroupModule,
        ArtemisSlideToggleModule,
        StructuredGradingCriterionModule,
    ],
    declarations: [TextExerciseComponent, TextExerciseDetailComponent, TextExerciseUpdateComponent],
    entryComponents: [TextExerciseUpdateComponent, DeleteDialogComponent],
    providers: [TextExerciseService],
    exports: [TextExerciseComponent],
})
export class ArtemisTextExerciseModule {}
