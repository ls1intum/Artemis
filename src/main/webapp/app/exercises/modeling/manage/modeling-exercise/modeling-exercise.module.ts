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
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor/modeling-editor.module';
import { ModelingExerciseUpdateComponent } from 'app/exercises/modeling/manage/modeling-exercise/modeling-exercise-update.component';
import { modelingExerciseRoute } from 'app/exercises/modeling/manage/modeling-exercise/modeling-exercise.route';
import { ModelingExerciseComponent } from 'app/exercises/modeling/manage/modeling-exercise/modeling-exercise.component';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise/modeling-exercise.service';
import { ModelingExerciseDetailComponent } from 'app/exercises/modeling/manage/modeling-exercise/modeling-exercise-detail.component';
import { ArtemisTeamConfigFormGroupModule } from 'app/exercises/shared/team-config-form-group/team-config-form-group.module';
import { StructuredGradingCriterionModule } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.module';
import { ArtemisSlideToggleModule } from 'app/exercises/shared/slide-toggle/slide-toggle.module';

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
        ArtemisPresentationScoreModule,
        ArtemisModelingEditorModule,
        ArtemisAssessmentSharedModule,
        ArtemisTeamConfigFormGroupModule,
        StructuredGradingCriterionModule,
        ArtemisSlideToggleModule,
    ],
    declarations: [ModelingExerciseComponent, ModelingExerciseDetailComponent, ModelingExerciseUpdateComponent],
    entryComponents: [ModelingExerciseComponent, DeleteDialogComponent],
    providers: [ModelingExerciseService],
    exports: [ModelingExerciseComponent],
})
export class ArtemisModelingExerciseModule {}
