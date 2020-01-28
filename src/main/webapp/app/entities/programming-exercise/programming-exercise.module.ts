import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared';
import { SortByModule } from 'app/components/pipes';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/components/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/components/exercise/difficulty-picker/difficulty-picker.module';
import { ArtemisResultModule } from 'app/entities/result';
import { ArtemisProgrammingExerciseTestCaseModule } from 'app/entities/programming-exercise/test-cases/programming-exercise-test-case.module';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/entities/programming-exercise/instructions/instructions-editor';
import { ArtemisProgrammingExerciseStatusModule } from 'app/entities/programming-exercise/status';
import { ArtemisProgrammingExerciseActionsModule } from 'app/entities/programming-exercise/actions/programming-exercise-actions.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisPresentationScoreModule } from 'app/components/exercise/presentation-score/presentation-score.module';
import {
    ProgrammingExerciseLifecycleComponent,
    ProgrammingExerciseTestScheduleDatePickerComponent,
} from 'app/entities/programming-exercise/programming-exercise-test-schedule-picker';
import { OwlDateTimeModule } from 'ng-pick-datetime';
import { ProgrammingExercisePlansAndRepositoriesPreviewComponent } from 'app/entities/programming-exercise/programming-exercise-plans-and-repositories-preview.component';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';
import { ArtemisComplaintsModule } from 'app/complaints';
import { FeatureToggleModule } from 'app/feature-toggle/feature-toggle.module';
import { ArtemisProgrammingAssessmentModule } from 'app/programming-assessment/programming-assessment.module';
import { IntellijModule } from 'app/intellij/intellij.module';
import { ProgrammingExerciseComponent } from 'app/entities/programming-exercise/programming-exercise.component';
import { ProgrammingExerciseUpdateComponent } from 'app/entities/programming-exercise/programming-exercise-update.component';
import { ProgrammingExerciseDetailComponent } from 'app/entities/programming-exercise/programming-exercise-detail.component';
import { ProgrammingExerciseImportComponent } from 'app/entities/programming-exercise/programming-exercise-import.component';
import { programmingExerciseRoute } from 'app/entities/programming-exercise/programming-exercise.route';
import { ProgrammingExerciseUtilsModule } from 'app/entities/programming-exercise/utils/programming-exercise-utils.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment-shared/assessment-shared.module';

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
        FeatureToggleModule,
        ArtemisProgrammingAssessmentModule,
        IntellijModule,
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
