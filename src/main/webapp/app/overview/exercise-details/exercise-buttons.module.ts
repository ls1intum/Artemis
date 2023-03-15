import { NgModule } from '@angular/core';

import { OrionExerciseDetailsStudentActionsComponent } from 'app/orion/participation/orion-exercise-details-student-actions.component';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisSharedPipesModule, ArtemisCoursesRoutingModule, OrionModule, FeatureToggleModule],
    declarations: [ExerciseDetailsStudentActionsComponent, OrionExerciseDetailsStudentActionsComponent],
    exports: [ExerciseDetailsStudentActionsComponent, OrionExerciseDetailsStudentActionsComponent],
})
export class ArtemisExerciseButtonsModule {}
