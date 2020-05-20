import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseManagementRoutingModule } from 'app/exercises/programming/manage/programming-exercise-management-routing.module';
import { ProgrammingExerciseUtilsModule } from 'app/exercises/programming/shared/utils/programming-exercise-utils.module';
import { ProgrammingExerciseDetailComponent } from 'app/exercises/programming/manage/programming-exercise-detail.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-instructions-editor.module';
import { ArtemisProgrammingExerciseUpdateModule } from 'app/exercises/programming/manage/update/programming-exercise-update.module';
import { ProgrammingExerciseImportComponent } from 'app/exercises/programming/manage/programming-exercise-import.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { ArtemisProgrammingExerciseModule } from 'app/exercises/programming/shared/programming-exercise.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ProgrammingExerciseUtilsModule,
        ArtemisSharedComponentModule,
        ArtemisResultModule,
        ArtemisAssessmentSharedModule,
        ArtemisProgrammingExerciseModule,
        ArtemisProgrammingExerciseManagementRoutingModule,
        ArtemisProgrammingAssessmentModule,
        ArtemisProgrammingExerciseActionsModule,
        ArtemisProgrammingExerciseInstructionsEditorModule,
        ArtemisProgrammingExerciseUpdateModule,
        ArtemisProgrammingExerciseStatusModule,
        FeatureToggleModule,
        SortByModule,
    ],
    declarations: [ProgrammingExerciseDetailComponent, ProgrammingExerciseImportComponent],
})
export class ArtemisProgrammingExerciseManagementModule {}
