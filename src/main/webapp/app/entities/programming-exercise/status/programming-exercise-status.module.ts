import { NgModule } from '@angular/core';
import { ProgrammingExerciseInstructorExerciseStatusComponent, ProgrammingExerciseInstructorStatusComponent } from './';
import { ArtemisSharedModule } from 'app/shared';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ProgrammingExerciseInstructorStatusComponent, ProgrammingExerciseInstructorExerciseStatusComponent],
    exports: [ProgrammingExerciseInstructorStatusComponent, ProgrammingExerciseInstructorExerciseStatusComponent],
})
export class ArtemisProgrammingExerciseStatusModule {}
