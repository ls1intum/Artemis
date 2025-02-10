import { NgModule } from '@angular/core';
import { ProgrammingExerciseComponent } from 'app/exercises/programming/manage/programming-exercise.component';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { ArtemisProgrammingExerciseGradingModule } from 'app/exercises/programming/manage/grading/programming-exercise-grading.module';
import { OrionProgrammingExerciseComponent } from 'app/orion/management/orion-programming-exercise.component';
import { ExerciseCategoriesModule } from 'app/shared/exercise-categories/exercise-categories.module';
import { ProgrammingExerciseResetButtonDirective } from 'app/exercises/programming/manage/reset/programming-exercise-reset-button.directive';
import { ProgrammingExerciseResetDialogComponent } from 'app/exercises/programming/manage/reset/programming-exercise-reset-dialog.component';
import { ProgrammingExerciseCreateButtonsComponent } from 'app/exercises/programming/manage/programming-exercise-create-buttons.component';
import { CommitsInfoComponent } from './commits-info/commits-info.component';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { CommitsInfoGroupComponent } from 'app/exercises/programming/shared/commits-info/commits-info-group/commits-info-group.component';
import { CommitsInfoRowComponent } from 'app/exercises/programming/shared/commits-info/commits-info-group/commits-info-row/commits-info-row.component';

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule,
        OrionModule,
        ArtemisProgrammingExerciseStatusModule,
        ArtemisProgrammingExerciseGradingModule,
        ExerciseCategoriesModule,
        SubmissionResultStatusModule,
        ProgrammingExerciseComponent,
        ProgrammingExerciseCreateButtonsComponent,
        OrionProgrammingExerciseComponent,
        ProgrammingExerciseResetButtonDirective,
        ProgrammingExerciseResetDialogComponent,
        CommitsInfoComponent,
        CommitsInfoGroupComponent,
        CommitsInfoRowComponent,
    ],
    exports: [
        ProgrammingExerciseComponent,
        ProgrammingExerciseCreateButtonsComponent,
        OrionProgrammingExerciseComponent,
        ProgrammingExerciseResetButtonDirective,
        CommitsInfoComponent,
    ],
})
export class ArtemisProgrammingExerciseModule {}
