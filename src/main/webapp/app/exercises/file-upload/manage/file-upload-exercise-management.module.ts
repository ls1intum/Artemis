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
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { StructuredGradingCriterionModule } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisCategorySelectorModule,
        ArtemisDifficultyPickerModule,
        ArtemisMarkdownEditorModule,
        ArtemisPresentationScoreModule,
        ArtemisAssessmentSharedModule,
        ArtemisFileUploadExerciseManagementRoutingModule,
        ArtemisTeamConfigFormGroupModule,
        FormDateTimePickerModule,
        SortByModule,
        StructuredGradingCriterionModule,
        AssessmentInstructionsModule,
    ],
    declarations: [FileUploadExerciseComponent, FileUploadExerciseDetailComponent, FileUploadExerciseUpdateComponent],
    exports: [FileUploadExerciseComponent],
})
export class ArtemisFileUploadExerciseManagementModule {}
