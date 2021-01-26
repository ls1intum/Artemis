import { NgModule } from '@angular/core';
import { ClipboardModule } from 'ngx-clipboard';
import { MomentModule } from 'ngx-moment';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ProgrammingExerciseStudentIdeActionsComponent } from 'app/overview/exercise-details/programming-exercise-student-ide-actions.component';
import { ExerciseActionButtonComponent } from 'app/overview/exercise-details/exercise-action-button.component';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ClipboardModule,
        MomentModule,
        ArtemisSharedPipesModule,
        ArtemisCoursesRoutingModule,
        OrionModule,
        FeatureToggleModule,
    ],
    declarations: [ExerciseActionButtonComponent, ExerciseDetailsStudentActionsComponent, ProgrammingExerciseStudentIdeActionsComponent],
    exports: [ExerciseActionButtonComponent, ExerciseDetailsStudentActionsComponent, ProgrammingExerciseStudentIdeActionsComponent],
})
export class ArtemisExerciseButtonsModule {}
