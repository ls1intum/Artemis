import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { ArtemisProgrammingExerciseGradingModule } from 'app/exercises/programming/manage/grading/programming-exercise-grading.module';
import { ProgrammingExerciseComponent } from 'app/exercises/programming/manage/programming-exercise.component';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { ArtemisExerciseScoresModule } from 'app/exercises/shared/exercise-scores/exercise-scores.module';
import { OrionProgrammingExerciseComponent } from 'app/orion/management/orion-programming-exercise.component';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        FeatureToggleModule,
        RouterModule,
        OrionModule,
        ArtemisProgrammingExerciseStatusModule,
        ArtemisProgrammingExerciseGradingModule,
        ArtemisExerciseScoresModule,
        ArtemisProgrammingAssessmentModule,
    ],
    declarations: [ProgrammingExerciseComponent, OrionProgrammingExerciseComponent],
    exports: [ProgrammingExerciseComponent, OrionProgrammingExerciseComponent],
})
export class ArtemisProgrammingExerciseModule {}
