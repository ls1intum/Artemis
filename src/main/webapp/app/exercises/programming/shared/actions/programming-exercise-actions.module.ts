import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-trigger-build-button.component';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-student-trigger-build-button.component';
import { ProgrammingExerciseInstructorSubmissionStateComponent } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-submission-state.component';
import {
    ProgrammingExerciseInstructorTriggerAllDialogComponent,
    ProgrammingExerciseTriggerAllButtonComponent,
} from 'app/exercises/programming/shared/actions/programming-exercise-trigger-all-button.component';
import { ProgrammingExerciseReEvaluateButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-re-evaluate-button.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ProgrammingExerciseInstructorRepoDownloadComponent } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-repo-download.component';
import { ProgrammingExerciseStudentRepoDownloadComponent } from 'app/exercises/programming/shared/actions/programming-exercise-student-repo-download.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, FeatureToggleModule],
    declarations: [
        ProgrammingExerciseInstructorTriggerBuildButtonComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        ProgrammingExerciseInstructorSubmissionStateComponent,
        ProgrammingExerciseInstructorTriggerAllDialogComponent,
        ProgrammingExerciseTriggerAllButtonComponent,
        ProgrammingExerciseReEvaluateButtonComponent,
        ProgrammingExerciseInstructorRepoDownloadComponent,
        ProgrammingExerciseStudentRepoDownloadComponent,
    ],
    exports: [
        ProgrammingExerciseInstructorTriggerBuildButtonComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        ProgrammingExerciseInstructorSubmissionStateComponent,
        ProgrammingExerciseTriggerAllButtonComponent,
        ProgrammingExerciseReEvaluateButtonComponent,
        ProgrammingExerciseInstructorRepoDownloadComponent,
        ProgrammingExerciseStudentRepoDownloadComponent,
    ],
})
export class ArtemisProgrammingExerciseActionsModule {}
