import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';
import { ArtemisPresentationScoreModule } from 'app/exercises/shared/presentation-score/presentation-score.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { TextExerciseUpdateComponent } from 'app/exercises/text/manage/text-exercise/text-exercise-update.component';
import { textExerciseRoute } from 'app/exercises/text/manage/text-exercise/text-exercise.route';
import { TextExerciseComponent } from 'app/exercises/text/manage/text-exercise/text-exercise.component';
import { TextExerciseDetailComponent } from 'app/exercises/text/manage/text-exercise/text-exercise-detail.component';
import { ArtemisTeamConfigFormGroupModule } from 'app/exercises/shared/team-config-form-group/team-config-form-group.module';
import { StructuredGradingCriterionModule } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';

import { TextExerciseRowButtonsComponent } from 'app/exercises/text/manage/text-exercise/text-exercise-row-buttons.component';
import { ArtemisIncludedInOverallScorePickerModule } from 'app/exercises/shared/included-in-overall-score-picker/included-in-overall-score-picker.module';
import { NonProgrammingExerciseDetailCommonActionsModule } from 'app/exercises/shared/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.module';
import { ArtemisExerciseUpdateWarningModule } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.module';
import { ExampleSubmissionsModule } from 'app/exercises/shared/example-submission/example-submissions.module';
import { ExerciseTitleChannelNameModule } from 'app/exercises/shared/exercise-title-channel-name/exercise-title-channel-name.module';
import { ExerciseUpdateNotificationModule } from 'app/exercises/shared/exercise-update-notification/exercise-update-notification.module';
import { ExerciseUpdatePlagiarismModule } from 'app/exercises/shared/plagiarism/exercise-update-plagiarism/exercise-update-plagiarism.module';
import { ExerciseFeedbackSuggestionOptionsModule } from 'app/exercises/shared/feedback-suggestion/exercise-feedback-suggestion-options.module';
import { DetailModule } from 'app/detail-overview-list/detail.module';
import { ArtemisExerciseModule } from 'app/exercises/shared/exercise/exercise.module';
import { ArtemisFormsModule } from 'app/forms/artemis-forms.module';

const ENTITY_STATES = [...textExerciseRoute];

@NgModule({
    imports: [
        RouterModule.forChild(ENTITY_STATES),
        FormDateTimePickerModule,
        ArtemisDifficultyPickerModule,
        ArtemisIncludedInOverallScorePickerModule,
        ArtemisPresentationScoreModule,
        ArtemisAssessmentSharedModule,
        ArtemisTeamConfigFormGroupModule,
        StructuredGradingCriterionModule,
        AssessmentInstructionsModule,

        NonProgrammingExerciseDetailCommonActionsModule,
        ArtemisExerciseUpdateWarningModule,
        ExampleSubmissionsModule,
        ArtemisExerciseModule,
        ExerciseTitleChannelNameModule,
        ExerciseUpdateNotificationModule,
        ExerciseUpdatePlagiarismModule,
        ExerciseFeedbackSuggestionOptionsModule,
        DetailModule,
        ArtemisFormsModule,
        TextExerciseComponent,
        TextExerciseDetailComponent,
        TextExerciseUpdateComponent,
        TextExerciseRowButtonsComponent,
    ],
    exports: [TextExerciseComponent],
})
export class ArtemisTextExerciseModule {}
