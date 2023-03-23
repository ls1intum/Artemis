import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/shared/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';
import { ArtemisPresentationScoreModule } from 'app/exercises/shared/presentation-score/presentation-score.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { FileUploadExerciseComponent } from 'app/exercises/file-upload/manage/file-upload-exercise.component';
import { FileUploadExerciseUpdateComponent } from 'app/exercises/file-upload/manage/file-upload-exercise-update.component';
import { FileUploadExerciseDetailComponent } from 'app/exercises/file-upload/manage/file-upload-exercise-detail.component';
import { ArtemisTeamConfigFormGroupModule } from 'app/exercises/shared/team-config-form-group/team-config-form-group.module';
import { ArtemisFileUploadExerciseManagementRoutingModule } from 'app/exercises/file-upload/manage/file-upload-exercise-management.route';
import { StructuredGradingCriterionModule } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ExerciseDetailsModule } from 'app/exercises/shared/exercise/exercise-details/exercise-details.module';
import { ArtemisIncludedInOverallScorePickerModule } from 'app/exercises/shared/included-in-overall-score-picker/included-in-overall-score-picker.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { NonProgrammingExerciseDetailCommonActionsModule } from 'app/exercises/shared/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisCategorySelectorModule,
        ArtemisIncludedInOverallScorePickerModule,
        ArtemisDifficultyPickerModule,
        ArtemisMarkdownEditorModule,
        ArtemisPresentationScoreModule,
        ArtemisAssessmentSharedModule,
        ArtemisFileUploadExerciseManagementRoutingModule,
        ArtemisTeamConfigFormGroupModule,
        FormDateTimePickerModule,
        StructuredGradingCriterionModule,
        AssessmentInstructionsModule,
        ExerciseDetailsModule,
        ArtemisMarkdownModule,
        NonProgrammingExerciseDetailCommonActionsModule,
        ArtemisSharedComponentModule,
    ],
    declarations: [FileUploadExerciseComponent, FileUploadExerciseDetailComponent, FileUploadExerciseUpdateComponent],
    exports: [FileUploadExerciseComponent],
})
export class ArtemisFileUploadExerciseManagementModule {}
