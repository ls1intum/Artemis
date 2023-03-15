import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ltiLaunchState } from './lti.route';
import { ArtemisCoreModule } from 'app/core/core.module';
import { Lti13DynamicRegistrationComponent } from 'app/lti/lti13-dynamic-registration.component';
import { Lti13ExerciseLaunchComponent } from 'app/lti/lti13-exercise-launch.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const LTI_LAUNCH_ROUTES = [...ltiLaunchState];

@NgModule({
    imports: [RouterModule.forChild(LTI_LAUNCH_ROUTES), ArtemisCoreModule, ArtemisSharedModule],
    declarations: [Lti13ExerciseLaunchComponent, Lti13DynamicRegistrationComponent],
    exports: [Lti13ExerciseLaunchComponent, Lti13DynamicRegistrationComponent],
})
export class ArtemisLtiModule {}
