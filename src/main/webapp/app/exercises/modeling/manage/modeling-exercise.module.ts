import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/shared/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';
import { ArtemisPresentationScoreModule } from 'app/exercises/shared/presentation-score/presentation-score.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ModelingExerciseUpdateComponent } from 'app/exercises/modeling/manage/modeling-exercise-update.component';
import { ArtemisModelingExerciseRoutingModule } from 'app/exercises/modeling/manage/modeling-exercise.route';
import { ModelingExerciseComponent } from 'app/exercises/modeling/manage/modeling-exercise.component';
import { ModelingExerciseDetailComponent } from 'app/exercises/modeling/manage/modeling-exercise-detail.component';
import { ArtemisTeamConfigFormGroupModule } from 'app/exercises/shared/team-config-form-group/team-config-form-group.module';
import { StructuredGradingCriterionModule } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ExerciseDetailsModule } from 'app/exercises/shared/exercise/exercise-details/exercise-details.module';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';
import { ArtemisIncludedInOverallScorePickerModule } from 'app/exercises/shared/included-in-overall-score-picker/included-in-overall-score-picker.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { NonProgrammingExerciseDetailCommonActionsModule } from 'app/exercises/shared/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.module';
import { ExampleSubmissionsModule } from 'app/exercises/shared/example-submission/example-submissions.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisMarkdownEditorModule,
        ArtemisCategorySelectorModule,
        ArtemisIncludedInOverallScorePickerModule,
        ArtemisDifficultyPickerModule,
        ArtemisPresentationScoreModule,
        ArtemisModelingEditorModule,
        ArtemisAssessmentSharedModule,
        ArtemisTeamConfigFormGroupModule,
        ArtemisModelingExerciseRoutingModule,
        ArtemisPlagiarismModule,
        FormDateTimePickerModule,
        StructuredGradingCriterionModule,
        AssessmentInstructionsModule,
        ExerciseDetailsModule,
        ArtemisMarkdownModule,
        NonProgrammingExerciseDetailCommonActionsModule,
        ExampleSubmissionsModule,
        ArtemisSharedComponentModule,
    ],
    declarations: [ModelingExerciseComponent, ModelingExerciseDetailComponent, ModelingExerciseUpdateComponent],
    exports: [ModelingExerciseComponent],
})
export class ArtemisModelingExerciseModule {}
