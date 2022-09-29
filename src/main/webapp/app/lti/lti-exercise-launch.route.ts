import { Routes } from '@angular/router';
import { ArtemisLtiExerciseLaunchComponent } from 'app/lti/lti-exercise-launch.component';

export const ltiLaunchRoutes: Routes = [
    {
        path: '',
        component: ArtemisLtiExerciseLaunchComponent,
        data: {
            pageTitle: 'artemisApp.lti13.launch.title',
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
