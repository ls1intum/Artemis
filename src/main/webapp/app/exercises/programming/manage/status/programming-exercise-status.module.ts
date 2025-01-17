import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ProgrammingExerciseInstructorStatusComponent } from 'app/exercises/programming/manage/status/programming-exercise-instructor-status.component';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from 'app/exercises/programming/manage/status/programming-exercise-instructor-exercise-status.component';

@NgModule({
    imports: [ArtemisSharedModule, ProgrammingExerciseInstructorStatusComponent, ProgrammingExerciseInstructorExerciseStatusComponent],
    exports: [ProgrammingExerciseInstructorStatusComponent, ProgrammingExerciseInstructorExerciseStatusComponent],
})
export class ArtemisProgrammingExerciseStatusModule {}
