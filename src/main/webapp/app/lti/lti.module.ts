import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { Lti13ExerciseLaunchComponent } from 'app/lti/lti13-exercise-launch.component';
import { Lti13DynamicRegistrationComponent } from 'app/lti/lti13-dynamic-registration.component';
import { ArtemisCoreModule } from 'app/core/core.module';
import { ltiLaunchState } from './lti.route';
import { Lti13DeepLinkingComponent } from 'app/lti/lti13-deep-linking.component';
import { FormsModule } from '@angular/forms';

const LTI_LAUNCH_ROUTES = [...ltiLaunchState];

@NgModule({
    imports: [RouterModule.forChild(LTI_LAUNCH_ROUTES), ArtemisCoreModule, ArtemisSharedModule, FormsModule],
    declarations: [Lti13ExerciseLaunchComponent, Lti13DynamicRegistrationComponent, Lti13DeepLinkingComponent],
    exports: [Lti13ExerciseLaunchComponent, Lti13DynamicRegistrationComponent, Lti13DeepLinkingComponent],
})
export class ArtemisLtiModule {}
