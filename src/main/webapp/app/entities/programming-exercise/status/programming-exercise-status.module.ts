import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ProgrammingExerciseInstructorStatusComponent } from 'app/entities/programming-exercise/status/programming-exercise-instructor-status.component';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from 'app/entities/programming-exercise/status/programming-exercise-instructor-exercise-status.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ProgrammingExerciseInstructorStatusComponent, ProgrammingExerciseInstructorExerciseStatusComponent],
    exports: [ProgrammingExerciseInstructorStatusComponent, ProgrammingExerciseInstructorExerciseStatusComponent],
})
export class ArtemisProgrammingExerciseStatusModule {}
