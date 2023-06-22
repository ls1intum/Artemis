import { NgModule } from '@angular/core';
import { ProgrammingExerciseComponent } from 'app/exercises/programming/manage/programming-exercise.component';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { ArtemisProgrammingExerciseGradingModule } from 'app/exercises/programming/manage/grading/programming-exercise-grading.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisExerciseScoresModule } from 'app/exercises/shared/exercise-scores/exercise-scores.module';
import { OrionProgrammingExerciseComponent } from 'app/orion/management/orion-programming-exercise.component';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { ExerciseCategoriesModule } from 'app/shared/exercise-categories/exercise-categories.module';
import { ProgrammingExerciseResetButtonDirective } from 'app/exercises/programming/manage/reset/programming-exercise-reset-button.directive';
import { ProgrammingExerciseResetDialogComponent } from 'app/exercises/programming/manage/reset/programming-exercise-reset-dialog.component';
import { ProgrammingExerciseCreateButtonsComponent } from 'app/exercises/programming/manage/programming-exercise-create-buttons.component';

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
        ExerciseCategoriesModule,
    ],
    declarations: [
        ProgrammingExerciseComponent,
        ProgrammingExerciseCreateButtonsComponent,
        OrionProgrammingExerciseComponent,
        ProgrammingExerciseResetButtonDirective,
        ProgrammingExerciseResetDialogComponent,
    ],
    exports: [ProgrammingExerciseComponent, ProgrammingExerciseCreateButtonsComponent, OrionProgrammingExerciseComponent, ProgrammingExerciseResetButtonDirective],
})
export class ArtemisProgrammingExerciseModule {}
