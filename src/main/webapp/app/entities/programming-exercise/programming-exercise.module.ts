import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/components/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/components/exercise/difficulty-picker/difficulty-picker.module';
import { ArtemisProgrammingExerciseTestCaseModule } from 'app/entities/programming-exercise/test-cases/programming-exercise-test-case.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/entities/programming-exercise/actions/programming-exercise-actions.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisPresentationScoreModule } from 'app/components/exercise/presentation-score/presentation-score.module';
import { OwlDateTimeModule } from 'ng-pick-datetime';
import { ProgrammingExercisePlansAndRepositoriesPreviewComponent } from 'app/entities/programming-exercise/programming-exercise-plans-and-repositories-preview.component';
import { AssessmentInstructionsModule } from 'app/assessment-instructions/assessment-instructions.module';
import { FeatureToggleModule } from 'app/feature-toggle/feature-toggle.module';
import { ArtemisProgrammingAssessmentModule } from 'app/programming-assessment/programming-assessment.module';
import { OrionModule } from 'app/orion/orion.module';
import { ProgrammingExerciseComponent } from 'app/entities/programming-exercise/programming-exercise.component';
import { ProgrammingExerciseUpdateComponent } from 'app/entities/programming-exercise/programming-exercise-update.component';
import { ProgrammingExerciseDetailComponent } from 'app/entities/programming-exercise/programming-exercise-detail.component';
import { ProgrammingExerciseImportComponent } from 'app/entities/programming-exercise/programming-exercise-import.component';
import { programmingExerciseRoute } from 'app/entities/programming-exercise/programming-exercise.route';
import { ProgrammingExerciseUtilsModule } from 'app/entities/programming-exercise/utils/programming-exercise-utils.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment-shared/assessment-shared.module';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/entities/programming-exercise/instructions/instructions-editor/programming-exercise-instructions-editor.module';
import { ArtemisResultModule } from 'app/entities/result/result.module';
import { SortByModule } from 'app/components/pipes/sort-by.module';
import { ProgrammingExerciseLifecycleComponent } from 'app/entities/programming-exercise/programming-exercise-test-schedule-picker/programming-exercise-lifecycle.component';
import { ProgrammingExerciseTestScheduleDatePickerComponent } from 'app/entities/programming-exercise/programming-exercise-test-schedule-picker/programming-exercise-test-schedule-date-picker.component';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor/markdown-editor.module';
import { ArtemisProgrammingExerciseStatusModule } from 'app/entities/programming-exercise/status/programming-exercise-status.module';
import { StructuredGradingCriterionModule } from 'app/structured-grading-criterion/structured-grading-criterion.module';

const ENTITY_STATES = [...programmingExerciseRoute];

@NgModule({
    imports: [
        // Shared modules.
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        FormDateTimePickerModule,
        ArtemisCategorySelectorModule,
        ArtemisDifficultyPickerModule,
        // Programming exercise sub modules.
        ArtemisProgrammingExerciseInstructionsEditorModule,
        ArtemisProgrammingExerciseStatusModule,
        ArtemisProgrammingExerciseActionsModule,
        ArtemisProgrammingExerciseTestCaseModule,
        // Other entity modules.
        ArtemisResultModule,
        ArtemisSharedComponentModule,
        ArtemisPresentationScoreModule,
        OwlDateTimeModule,
        ArtemisMarkdownEditorModule,
        ArtemisComplaintsModule,
        AssessmentInstructionsModule,
        FeatureToggleModule,
        ArtemisProgrammingAssessmentModule,
        OrionModule,
        StructuredGradingCriterionModule,
        ProgrammingExerciseUtilsModule,
        ArtemisAssessmentSharedModule,
    ],
    declarations: [
        ProgrammingExerciseComponent,
        ProgrammingExerciseDetailComponent,
        ProgrammingExerciseUpdateComponent,
        ProgrammingExerciseImportComponent,
        ProgrammingExercisePlansAndRepositoriesPreviewComponent,
        // Form components
        ProgrammingExerciseLifecycleComponent,
        ProgrammingExerciseTestScheduleDatePickerComponent,
    ],
    entryComponents: [ProgrammingExerciseComponent, ProgrammingExerciseUpdateComponent, ProgrammingExerciseImportComponent],
    exports: [ProgrammingExerciseComponent, ArtemisProgrammingExerciseInstructionsEditorModule, ArtemisProgrammingExerciseActionsModule],
})
export class ArtemisProgrammingExerciseModule {}
