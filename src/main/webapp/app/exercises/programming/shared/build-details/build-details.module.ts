import { NgModule } from '@angular/core';
import { ProgrammingExerciseBuildDetailsComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-build-details.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-build-plan-checkout-directories.component';

@NgModule({
    imports: [ArtemisSharedComponentModule, ArtemisSharedCommonModule],
    declarations: [ProgrammingExerciseBuildDetailsComponent, ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent],
    exports: [ProgrammingExerciseBuildDetailsComponent],
})
export class ArtemisProgrammingExerciseBuildDetailsModule {}
