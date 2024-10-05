import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { Lti13ExerciseLaunchComponent } from 'app/lti/lti13-exercise-launch.component';
import { Lti13DynamicRegistrationComponent } from 'app/lti/lti13-dynamic-registration.component';
import { ltiLaunchState } from './lti.route';
import { Lti13DeepLinkingComponent } from 'app/lti/lti13-deep-linking.component';
import { FormsModule } from '@angular/forms';
import { Lti13SelectContentComponent } from 'app/lti/lti13-select-content.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { LtiCoursesComponent } from 'app/lti/lti13-select-course.component';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';
import { LtiCourseCardComponent } from 'app/lti/lti-course-card.component';

const LTI_LAUNCH_ROUTES = [...ltiLaunchState];

@NgModule({
    imports: [RouterModule.forChild(LTI_LAUNCH_ROUTES), ArtemisSharedModule, FormsModule, ArtemisSharedComponentModule, ArtemisSharedLibsModule],
    declarations: [
        Lti13ExerciseLaunchComponent,
        Lti13DynamicRegistrationComponent,
        Lti13DeepLinkingComponent,
        Lti13SelectContentComponent,
        LtiCoursesComponent,
        LtiCourseCardComponent,
    ],
    exports: [Lti13ExerciseLaunchComponent, Lti13DynamicRegistrationComponent, Lti13DeepLinkingComponent, Lti13SelectContentComponent, LtiCoursesComponent],
})
export class ArtemisLtiModule {}
