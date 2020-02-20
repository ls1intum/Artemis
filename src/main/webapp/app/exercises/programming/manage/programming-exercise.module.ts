import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/shared/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';
import { ArtemisProgrammingExerciseTestCaseModule } from 'app/exercises/programming/manage/test-cases/programming-exercise-test-case.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisPresentationScoreModule } from 'app/exercises/shared/presentation-score/presentation-score.module';
import { OwlDateTimeModule } from 'ng-pick-datetime';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment/programming-assessment.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { ProgrammingExerciseUtilsModule } from 'app/exercises/programming/manage/utils/programming-exercise-utils.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/exercises/programming/manage/instructions/instructions-editor/programming-exercise-instructions-editor.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { ArtemisTeamConfigFormGroupModule } from 'app/exercises/shared/team-config-form-group/team-config-form-group.module';

@NgModule({
    imports: [
        // Shared modules.
        ArtemisSharedModule,
        SortByModule,
        FormDateTimePickerModule,
        ArtemisCategorySelectorModule,
        ArtemisDifficultyPickerModule,
        // Programming exercise sub modules.
        ArtemisProgrammingExerciseStatusModule,
        ArtemisProgrammingExerciseTestCaseModule,
        // Other entity modules.
        ArtemisResultModule,
        ArtemisSharedComponentModule,
        ArtemisPresentationScoreModule,
        OwlDateTimeModule,
        ArtemisMarkdownEditorModule,
        ArtemisComplaintsModule,
        FeatureToggleModule,
        ArtemisProgrammingAssessmentModule,
        OrionModule,
        ProgrammingExerciseUtilsModule,
        ArtemisAssessmentSharedModule,
        ArtemisTeamConfigFormGroupModule,
    ],
    exports: [ArtemisProgrammingExerciseInstructionsEditorModule],
})
export class ArtemisProgrammingExerciseModule {}
