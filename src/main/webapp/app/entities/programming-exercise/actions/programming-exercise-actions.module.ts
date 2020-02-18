import { NgModule } from '@angular/core';
import { MomentModule } from 'ngx-moment';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/entities/programming-exercise/actions/programming-exercise-instructor-trigger-build-button.component';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/entities/programming-exercise/actions/programming-exercise-student-trigger-build-button.component';
import { ProgrammingExerciseInstructorSubmissionStateComponent } from 'app/entities/programming-exercise/actions/programming-exercise-instructor-submission-state.component';
import {
    ProgrammingExerciseInstructorTriggerAllDialogComponent,
    ProgrammingExerciseTriggerAllButtonComponent,
} from 'app/entities/programming-exercise/actions/programming-exercise-trigger-all-button.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FeatureToggleModule } from 'app/feature-toggle/feature-toggle.module';

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, ArtemisSharedComponentModule, FeatureToggleModule],
    declarations: [
        ProgrammingExerciseInstructorTriggerBuildButtonComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        ProgrammingExerciseInstructorSubmissionStateComponent,
        ProgrammingExerciseInstructorTriggerAllDialogComponent,
        ProgrammingExerciseTriggerAllButtonComponent,
    ],
    exports: [
        ProgrammingExerciseInstructorTriggerBuildButtonComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        ProgrammingExerciseInstructorSubmissionStateComponent,
        ProgrammingExerciseTriggerAllButtonComponent,
    ],
    entryComponents: [ProgrammingExerciseInstructorTriggerAllDialogComponent],
})
export class ArtemisProgrammingExerciseActionsModule {}
