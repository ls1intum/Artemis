import { NgModule } from '@angular/core';
import { BuildPlanLinkDirective } from 'app/entities/programming-exercise/utils/build-plan-link.directive';
import { BuildPlanButtonDirective } from 'app/entities/programming-exercise/utils/build-plan-button.directive';

@NgModule({
    declarations: [BuildPlanLinkDirective, BuildPlanButtonDirective],
    exports: [BuildPlanLinkDirective, BuildPlanButtonDirective],
})
export class ProgrammingExerciseUtilsModule {}
