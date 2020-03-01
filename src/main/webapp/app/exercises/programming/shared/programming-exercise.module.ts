import { NgModule } from '@angular/core';
import { ProgrammingExerciseComponent } from 'app/exercises/programming/shared/programming-exercise.component';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { ArtemisProgrammingExerciseTestCaseModule } from 'app/exercises/programming/manage/test-cases/programming-exercise-test-case.module';
import { ProgrammingExerciseUtilsModule } from 'app/exercises/programming/shared/utils/programming-exercise-utils.module';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        SortByModule,
        FeatureToggleModule,
        RouterModule,
        OrionModule,
        ArtemisProgrammingExerciseStatusModule,
        ArtemisProgrammingExerciseTestCaseModule,
        ProgrammingExerciseUtilsModule,
    ],
    declarations: [ProgrammingExerciseComponent],
    exports: [ProgrammingExerciseComponent],
})
export class ArtemisProgrammingExerciseModule {}
