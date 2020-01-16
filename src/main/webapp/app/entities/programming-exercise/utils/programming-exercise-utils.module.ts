import { NgModule } from '@angular/core';
import { BuildPlanLinkDirective } from 'app/entities/programming-exercise/utils/build-plan-link.directive';

@NgModule({
    declarations: [BuildPlanLinkDirective],
    exports: [BuildPlanLinkDirective],
})
export class ProgrammingExerciseUtilsModule {}
