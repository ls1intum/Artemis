import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { Lti13ExerciseLaunchComponent } from 'app/lti/lti13-exercise-launch.component';
import { Lti13DynamicRegistrationComponent } from 'app/lti/lti13-dynamic-registration.component';
import { ArtemisCoreModule } from 'app/core/core.module';
import { ltiLaunchState } from './lti.route';
import { Lti13DeepLinkingComponent } from 'app/lti/lti13-deep-linking.component';
import { FormsModule } from '@angular/forms';
import { Lti13SelectContentComponent } from 'app/lti/lti13-select-content.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

const LTI_LAUNCH_ROUTES = [...ltiLaunchState];

@NgModule({
    imports: [RouterModule.forChild(LTI_LAUNCH_ROUTES), ArtemisCoreModule, ArtemisSharedModule, FormsModule, ArtemisSharedComponentModule],
    declarations: [Lti13ExerciseLaunchComponent, Lti13DynamicRegistrationComponent, Lti13DeepLinkingComponent, Lti13SelectContentComponent],
    exports: [Lti13ExerciseLaunchComponent, Lti13DynamicRegistrationComponent, Lti13DeepLinkingComponent, Lti13SelectContentComponent],
})
export class ArtemisLtiModule {}
