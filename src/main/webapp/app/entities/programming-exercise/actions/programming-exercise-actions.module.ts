import { NgModule } from '@angular/core';
import { MomentModule } from 'ngx-moment';
import { ArtemisSharedModule } from 'app/shared';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/entities/programming-exercise/actions/programming-exercise-instructor-trigger-build-button.component';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/entities/programming-exercise/actions/programming-exercise-student-trigger-build-button.component';
import {
    ProgrammingExerciseInstructorTriggerAllDialogComponent,
    ProgrammmingExerciseInstructorSubmissionStateComponent,
} from 'app/entities/programming-exercise/actions/programmming-exercise-instructor-submission-state.component';

@NgModule({
    imports: [ArtemisSharedModule, MomentModule],
    declarations: [
        ProgrammingExerciseInstructorTriggerBuildButtonComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        ProgrammmingExerciseInstructorSubmissionStateComponent,
        ProgrammingExerciseInstructorTriggerAllDialogComponent,
    ],
    exports: [
        ProgrammingExerciseInstructorTriggerBuildButtonComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        ProgrammmingExerciseInstructorSubmissionStateComponent,
    ],
    entryComponents: [ProgrammingExerciseInstructorTriggerAllDialogComponent],
})
export class ArtemisProgrammingExerciseActionsModule {}
