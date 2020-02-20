import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ProgrammingExerciseManagementRoutingModule } from 'app/exercises/programming/manage/programming-exercise-management-routing.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisProgrammingExerciseTestCaseModule } from 'app/exercises/programming/manage/test-cases/programming-exercise-test-case.module';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { ProgrammingExerciseUtilsModule } from 'app/exercises/programming/manage/utils/programming-exercise-utils.module';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { ProgrammingExerciseComponent } from 'app/exercises/programming/manage/programming-exercise.component';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ProgrammingExerciseManagementRoutingModule,
        OrionModule,
        FeatureToggleModule,
        ArtemisProgrammingExerciseTestCaseModule,
        ArtemisProgrammingExerciseStatusModule,
        ProgrammingExerciseUtilsModule,
        SortByModule,
    ],
    declarations: [ProgrammingExerciseComponent],
    exports: [ProgrammingExerciseComponent],
})
export class ProgrammingExerciseManagementModule {}
