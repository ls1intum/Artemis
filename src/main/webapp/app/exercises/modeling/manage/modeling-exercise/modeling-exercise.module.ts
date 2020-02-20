import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/shared/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { ArtemisPresentationScoreModule } from 'app/exercises/shared/presentation-score/presentation-score.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor/modeling-editor.module';
import { ModelingExerciseUpdateComponent } from 'app/exercises/modeling/manage/modeling-exercise/modeling-exercise-update.component';
import { ArtemisModelingExerciseRoutingModule } from 'app/exercises/modeling/manage/modeling-exercise/modeling-exercise.route';
import { ModelingExerciseComponent } from 'app/exercises/modeling/manage/modeling-exercise/modeling-exercise.component';
import { ModelingExerciseDetailComponent } from 'app/exercises/modeling/manage/modeling-exercise/modeling-exercise-detail.component';
import { ArtemisTeamConfigFormGroupModule } from 'app/exercises/shared/team-config-form-group/team-config-form-group.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisMarkdownEditorModule,
        ArtemisCategorySelectorModule,
        ArtemisDifficultyPickerModule,
        ArtemisPresentationScoreModule,
        ArtemisModelingEditorModule,
        ArtemisAssessmentSharedModule,
        ArtemisTeamConfigFormGroupModule,
        ArtemisModelingExerciseRoutingModule,
        FormDateTimePickerModule,
        SortByModule,
    ],
    declarations: [ModelingExerciseComponent, ModelingExerciseDetailComponent, ModelingExerciseUpdateComponent],
    entryComponents: [ModelingExerciseComponent, DeleteDialogComponent],
    exports: [ModelingExerciseComponent],
})
export class ArtemisModelingExerciseModule {}
