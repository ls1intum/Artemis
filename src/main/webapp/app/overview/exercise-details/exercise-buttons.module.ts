import { NgModule } from '@angular/core';

import { OrionExerciseDetailsStudentActionsComponent } from 'app/orion/participation/orion-exercise-details-student-actions.component';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';

import { RequestFeedbackButtonComponent } from 'app/overview/exercise-details/request-feedback-button/request-feedback-button.component';

@NgModule({
    imports: [RequestFeedbackButtonComponent, ExerciseDetailsStudentActionsComponent, OrionExerciseDetailsStudentActionsComponent],
    exports: [ExerciseDetailsStudentActionsComponent, OrionExerciseDetailsStudentActionsComponent],
})
export class ArtemisExerciseButtonsModule {}
