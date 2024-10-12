import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { OrionExerciseDetailsStudentActionsComponent } from 'app/orion/participation/orion-exercise-details-student-actions.component';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { RequestFeedbackButtonComponent } from 'app/overview/exercise-details/request-feedback-button/request-feedback-button.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisSharedPipesModule, OrionModule, FeatureToggleModule, RequestFeedbackButtonComponent],
    declarations: [ExerciseDetailsStudentActionsComponent, OrionExerciseDetailsStudentActionsComponent],
    exports: [ExerciseDetailsStudentActionsComponent, OrionExerciseDetailsStudentActionsComponent],
})
export class ArtemisExerciseButtonsModule {}
