import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { ArtemisLtiExerciseLaunchComponent } from 'app/lti/lti-exercise-launch.component';
import { ArtemisCoreModule } from 'app/core/core.module';
import { ltiLaunchState } from './lti-exercise-launch.route';

const LTI_LAUNCH_ROUTES = [...ltiLaunchState];

@NgModule({
    imports: [RouterModule.forChild(LTI_LAUNCH_ROUTES), ArtemisCoreModule, ArtemisSharedModule],
    declarations: [ArtemisLtiExerciseLaunchComponent],
    exports: [ArtemisLtiExerciseLaunchComponent],
})
export class ArtemisLtiExerciseLaunchModule {}
