import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/shared/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';
import { ArtemisProgrammingExerciseTestCaseModule } from 'app/exercises/programming/manage/test-cases/programming-exercise-test-case.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/manage/actions/programming-exercise-actions.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisPresentationScoreModule } from 'app/exercises/shared/presentation-score/presentation-score.module';
import { OwlDateTimeModule } from 'ng-pick-datetime';
import { ProgrammingExercisePlansAndRepositoriesPreviewComponent } from 'app/exercises/programming/manage/programming-exercise-plans-and-repositories-preview.component';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment/programming-assessment.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { ProgrammingExerciseComponent } from 'app/exercises/programming/manage/programming-exercise.component';
import { ProgrammingExerciseUpdateComponent } from 'app/exercises/programming/manage/programming-exercise-update.component';
import { ProgrammingExerciseDetailComponent } from 'app/exercises/programming/manage/programming-exercise-detail.component';
import { ProgrammingExerciseImportComponent } from 'app/exercises/programming/manage/programming-exercise-import.component';
import { programmingExerciseRoute } from 'app/exercises/programming/manage/programming-exercise.route';
import { ProgrammingExerciseUtilsModule } from 'app/exercises/programming/manage/utils/programming-exercise-utils.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/exercises/programming/manage/instructions/instructions-editor/programming-exercise-instructions-editor.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { ProgrammingExerciseLifecycleComponent } from 'app/exercises/programming/manage/programming-exercise-test-schedule-picker/programming-exercise-lifecycle.component';
import { ProgrammingExerciseTestScheduleDatePickerComponent } from 'app/exercises/programming/manage/programming-exercise-test-schedule-picker/programming-exercise-test-schedule-date-picker.component';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { ArtemisTeamConfigFormGroupModule } from 'app/exercises/shared/team-config-form-group/team-config-form-group.module';

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
        OrionModule,
        ProgrammingExerciseUtilsModule,
        ArtemisAssessmentSharedModule,
        ArtemisTeamConfigFormGroupModule,
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
