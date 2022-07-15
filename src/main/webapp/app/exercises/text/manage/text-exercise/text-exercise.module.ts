import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/shared/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';
import { ArtemisPresentationScoreModule } from 'app/exercises/shared/presentation-score/presentation-score.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { TextExerciseUpdateComponent } from 'app/exercises/text/manage/text-exercise/text-exercise-update.component';
import { textExerciseRoute } from 'app/exercises/text/manage/text-exercise/text-exercise.route';
import { TextExerciseComponent } from 'app/exercises/text/manage/text-exercise/text-exercise.component';
import { TextExerciseDetailComponent } from 'app/exercises/text/manage/text-exercise/text-exercise-detail.component';
import { ArtemisTeamConfigFormGroupModule } from 'app/exercises/shared/team-config-form-group/team-config-form-group.module';
import { StructuredGradingCriterionModule } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ExerciseDetailsModule } from 'app/exercises/shared/exercise/exercise-details/exercise-details.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { TextExerciseRowButtonsComponent } from 'app/exercises/text/manage/text-exercise/text-exercise-row-buttons.component';
import { ArtemisIncludedInOverallScorePickerModule } from 'app/exercises/shared/included-in-overall-score-picker/included-in-overall-score-picker.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { NonProgrammingExerciseDetailCommonActionsModule } from 'app/exercises/shared/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.module';
import { ArtemisExerciseUpdateWarningModule } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.module';
import { ExampleSubmissionsModule } from 'app/exercises/shared/example-submission/example-submissions.module';

const ENTITY_STATES = [...textExerciseRoute];

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
    ],
    declarations: [TextExerciseComponent, TextExerciseDetailComponent, TextExerciseUpdateComponent, TextExerciseRowButtonsComponent],
    exports: [TextExerciseComponent],
})
export class ArtemisTextExerciseModule {}
