import { NgModule } from '@angular/core';

import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';
import { ArtemisPresentationScoreModule } from 'app/exercises/shared/presentation-score/presentation-score.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { FileUploadExerciseComponent } from 'app/exercises/file-upload/manage/file-upload-exercise.component';
import { FileUploadExerciseUpdateComponent } from 'app/exercises/file-upload/manage/file-upload-exercise-update.component';
import { FileUploadExerciseDetailComponent } from 'app/exercises/file-upload/manage/file-upload-exercise-detail.component';
import { ArtemisTeamConfigFormGroupModule } from 'app/exercises/shared/team-config-form-group/team-config-form-group.module';
import { ArtemisFileUploadExerciseManagementRoutingModule } from 'app/exercises/file-upload/manage/file-upload-exercise-management.route';
import { StructuredGradingCriterionModule } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisIncludedInOverallScorePickerModule } from 'app/exercises/shared/included-in-overall-score-picker/included-in-overall-score-picker.module';
import { NonProgrammingExerciseDetailCommonActionsModule } from 'app/exercises/shared/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.module';

import { ExerciseTitleChannelNameModule } from 'app/exercises/shared/exercise-title-channel-name/exercise-title-channel-name.module';
import { ExerciseUpdateNotificationModule } from 'app/exercises/shared/exercise-update-notification/exercise-update-notification.module';
import { DetailModule } from 'app/detail-overview-list/detail.module';
import { ArtemisExerciseModule } from 'app/exercises/shared/exercise/exercise.module';
import { ArtemisFormsModule } from 'app/forms/artemis-forms.module';

@NgModule({
    imports: [
        ArtemisIncludedInOverallScorePickerModule,
        ArtemisDifficultyPickerModule,
        ArtemisPresentationScoreModule,
        ArtemisAssessmentSharedModule,
        ArtemisFileUploadExerciseManagementRoutingModule,
        ArtemisTeamConfigFormGroupModule,
        FormDateTimePickerModule,
        StructuredGradingCriterionModule,
        AssessmentInstructionsModule,
        NonProgrammingExerciseDetailCommonActionsModule,

        ArtemisExerciseModule,
        ExerciseTitleChannelNameModule,
        ExerciseUpdateNotificationModule,
        DetailModule,
        ArtemisFormsModule,
        FileUploadExerciseComponent,
        FileUploadExerciseDetailComponent,
        FileUploadExerciseUpdateComponent,
    ],
    exports: [FileUploadExerciseComponent],
})
export class ArtemisFileUploadExerciseManagementModule {}
