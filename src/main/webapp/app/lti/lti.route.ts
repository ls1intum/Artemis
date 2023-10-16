import { Routes } from '@angular/router';
import { Lti13ExerciseLaunchComponent } from 'app/lti/lti13-exercise-launch.component';
import { Lti13DynamicRegistrationComponent } from 'app/lti/lti13-dynamic-registration.component';
import { Lti13DeepLinkingComponent } from 'app/lti/lti13-deep-linking.component';

export const ltiLaunchRoutes: Routes = [
    {
        path: 'launch',
        component: Lti13ExerciseLaunchComponent,
        data: {
            pageTitle: 'artemisApp.lti13.launch.title',
        },
    },
    {
        path: 'dynamic-registration/:courseId',
        component: Lti13DynamicRegistrationComponent,
        data: {
            pageTitle: 'artemisApp.lti13.dynamicRegistration.title',
        },
    },
    {
        path: 'deep-linking',
        component: Lti13DeepLinkingComponent,
        data: {
            pageTitle: 'artemisApp.lti13.deepLinking.title',
        },
    },
];

const LTI_LAUNCH_ROUTES = [...ltiLaunchRoutes];

export const ltiLaunchState: Routes = [
    {
        path: '',
        children: LTI_LAUNCH_ROUTES,
    },
];
