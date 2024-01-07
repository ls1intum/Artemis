import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisCategorySelectorModule } from 'app/shared/category-selector/category-selector.module';
import { ExerciseCategoriesModule } from 'app/shared/exercise-categories/exercise-categories.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ExampleSubmissionsModule } from 'app/exercises/shared/example-submission/example-submissions.module';
import { ExerciseDetailsModule } from 'app/exercises/shared/exercise/exercise-details/exercise-details.module';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';
import { ArtemisPresentationScoreModule } from 'app/exercises/shared/presentation-score/presentation-score.module';
import { ArtemisTeamConfigFormGroupModule } from 'app/exercises/shared/team-config-form-group/team-config-form-group.module';
import { ArtemisExerciseUpdateWarningModule } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.module';
import { ExerciseTitleChannelNameModule } from 'app/exercises/shared/exercise-title-channel-name/exercise-title-channel-name.module';
import { ExerciseUpdateNotificationModule } from 'app/exercises/shared/exercise-update-notification/exercise-update-notification.module';
import { StructuredGradingCriterionModule } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.module';
import { ExerciseUpdatePlagiarismModule } from 'app/exercises/shared/plagiarism/exercise-update-plagiarism/exercise-update-plagiarism.module';
import { ArtemisIncludedInOverallScorePickerModule } from 'app/exercises/shared/included-in-overall-score-picker/included-in-overall-score-picker.module';
import { NonProgrammingExerciseDetailCommonActionsModule } from 'app/exercises/shared/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.module';

import { mathExerciseRoute } from './math-exercise.route';
import { MathExerciseComponent } from './math-exercise.component';
import { MathExerciseDetailComponent } from './math-exercise-detail.component';
import { MathExerciseRowButtonsComponent } from './math-exercise-row-buttons.component';
import { ArtemisMathExerciseComposeModule } from 'app/exercises/math/compose/math-exercise-compose.module';
import { MathExerciseEditComponent } from 'app/exercises/math/manage/math-exercise-edit.component';

const ENTITY_STATES = [...mathExerciseRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        FormDateTimePickerModule,
        ArtemisCategorySelectorModule,
        ArtemisDifficultyPickerModule,
        ArtemisIncludedInOverallScorePickerModule,
        ArtemisMarkdownEditorModule,
        ArtemisPresentationScoreModule,
        ArtemisAssessmentSharedModule,
        ArtemisTeamConfigFormGroupModule,
        StructuredGradingCriterionModule,
        AssessmentInstructionsModule,
        ExerciseDetailsModule,
        ArtemisSharedComponentModule,
        ArtemisMarkdownModule,
        NonProgrammingExerciseDetailCommonActionsModule,
        ArtemisExerciseUpdateWarningModule,
        ExampleSubmissionsModule,
        ExerciseCategoriesModule,
        ExerciseTitleChannelNameModule,
        ExerciseUpdateNotificationModule,
        ExerciseUpdatePlagiarismModule,
        ArtemisMathExerciseComposeModule,
    ],
    declarations: [MathExerciseComponent, MathExerciseDetailComponent, MathExerciseEditComponent, MathExerciseRowButtonsComponent],
    exports: [MathExerciseComponent],
})
export class ArtemisMathExerciseModule {}
