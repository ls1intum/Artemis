import { NgModule } from '@angular/core';
import { MomentModule } from 'ngx-moment';
import { ArtemisSharedModule } from 'app/shared';
import {
    ProgrammingExerciseInstructorTriggerBuildButtonComponent,
    ProgrammingExerciseInstructorTriggerBuildDialogComponent,
} from 'app/entities/programming-exercise/actions/programming-exercise-instructor-trigger-build-button.component';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/entities/programming-exercise/actions/programming-exercise-student-trigger-build-button.component';
import { ProgrammmingExerciseInstructorSubmissionStateComponent } from 'app/entities/programming-exercise/actions/programmming-exercise-instructor-submission-state.component';
import {
    ProgrammingExerciseInstructorTriggerAllDialogComponent,
    ProgrammingExerciseTriggerAllButtonComponent,
} from 'app/entities/programming-exercise/actions/programming-exercise-trigger-all-button.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, ArtemisSharedComponentModule],
    declarations: [
        ProgrammingExerciseInstructorTriggerBuildButtonComponent,
        ProgrammingExerciseInstructorTriggerBuildDialogComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        ProgrammmingExerciseInstructorSubmissionStateComponent,
        ProgrammingExerciseInstructorTriggerAllDialogComponent,
        ProgrammingExerciseTriggerAllButtonComponent,
    ],
    exports: [
        ProgrammingExerciseInstructorTriggerBuildButtonComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        ProgrammmingExerciseInstructorSubmissionStateComponent,
        ProgrammingExerciseTriggerAllButtonComponent,
    ],
    entryComponents: [ProgrammingExerciseInstructorTriggerAllDialogComponent, ProgrammingExerciseInstructorTriggerBuildDialogComponent],
})
export class ArtemisProgrammingExerciseActionsModule {}
